# 云同步登录与自动推送 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录回到「我的」（微信正式登录 + 绑手机）；开发面板只切环境；进 App 拉列表+当前本；改一条写数据库对应行（同条后写覆盖）；未绑云才显示上传；登录后新建/已登录 CSV 导入立刻上云。

**Architecture:** 服务器数据库为真相，客户端本地为缓存。去掉整本 `baseRevision` 导致的 409。补 `POST /ledgers` 空本。绑手机改为微信取号（不再用短信 `000000`）。Android / 小程序对等改 UI 与同步编排。

**Tech Stack:** Spring Boot 3 + MockMvc；Android Compose + Retrofit + 微信开放 SDK；小程序 `wx.login` / `getPhoneNumber`。Android 收尾 `sh oneClickSetup`。

**Git：** 工作区禁止自动 git。本计划 **不** 执行 commit。

**Spec：** `docs/superpowers/specs/2026-08-14-cloud-login-and-auto-sync-design.md`

---

## File map

### Server `/Users/beike/Projects/renovation-ledger-server`

| 路径 | 职责 |
|------|------|
| `ledger/ItemSyncService.kt` | PUT/DELETE 后写覆盖，不再因 `baseRevision` 返回 409 |
| `ledger/LedgerService.kt` `LedgerController.kt` `LedgerDtos.kt` | `POST /ledgers` 建空本 |
| `wechat/WeChatClient.kt` | 增加 `phoneFromCode` |
| `auth/AuthDtos.kt` `AuthService.kt` `AuthController.kt` | 微信取号绑手机；`AuthResponse.phone` |
| `src/test/.../ItemSyncServiceTest.kt` `BindPhoneTest.kt` | 改断言 |

### Android `~/Projects/renovation-ledger`

| 路径 | 职责 |
|------|------|
| `data/remote/ApiModels.kt` `LedgerApi.kt` | 列表 DTO、建空本、wechat/bind-phone |
| `data/sync/LedgerSyncRepository.kt` | 拉列表、建云本、PUT 不再当 409 为冲突 |
| `data/prefs/UserPrefs.kt` | `phone` |
| `ui/mine/*` | 微信登录、开发登录（仅 Debug）、未绑定才上传、绑手机 |
| `ui/debug/*` | 去掉登录 |
| `ui/overview/*` | 进页拉列表+当前本；新建走云 |
| `ui/importbatch/BatchImportConfirmViewModel.kt` | 已登录导入后 import |
| `wxapi/WXEntryActivity.kt` + `build.gradle.kts` | 微信 SDK |

### 小程序 `~/Projects/renovation-ledger-miniprogram`

| 路径 | 职责 |
|------|------|
| `utils/sync.js` `utils/api.js` | 列表、建空本、微信登录、绑手机、去掉 409 覆盖逻辑 |
| `pages/mine/*` | 与 Android 对等 |
| `pages/debug/*` | 去掉登录 |
| `pages/overview/overview.js` `utils/store.js` | 进页拉取、新建上云、导入上云 |

---

### Task 1: 服务端后写覆盖（去掉整本 409）

**Files:**
- Test: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/ledger/ItemSyncServiceTest.kt`
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/ledger/ItemSyncService.kt`

- [ ] **Step 1: 改测试：同条后写成功；不同条互不影响**

把 `staleRevisionReturns409` 换成后写覆盖，并加不同 item 双写：

```kotlin
@Test
fun sameItemLastWriteWins() {
    val token = login("sync2")
    val ledger = import(token)
    putItem(token, ledger.id, ledger.items[0].copy(name = "A"), ledger.revision, 200)
    putItem(token, ledger.id, ledger.items[0].copy(name = "B"), ledger.revision, 200)
    val again = getLedger(token, ledger.id)
    assertEquals("B", again.items[0].name)
}

@Test
fun differentItemsBothSucceed() {
    val token = login("sync3")
    val first = import(token)
    val second = ItemDto(
        id = "item_2",
        name = "地板",
        stage = "硬装",
        category = "地面",
        space = "客厅",
        budgetAmount = 20000,
    )
    putItem(token, first.id, second, first.revision, 200)
    putItem(token, first.id, first.items[0].copy(name = "灯具-甲"), first.revision, 200)
    val again = getLedger(token, first.id)
    assertEquals(2, again.items.size)
    assertEquals("灯具-甲", again.items.first { it.id == "item_1" }.name)
    assertEquals("地板", again.items.first { it.id == "item_2" }.name)
}
```

`import()` 每次用不同 `localId`（例如 `"proj_" + code`），避免绑定撞车。`login("sync3")` 的 import `localId` 用 `"proj_sync3"`。

- [ ] **Step 2: 跑测试，确认 `sameItemLastWriteWins` 因 409 失败**

Run:

```bash
cd /Users/beike/Projects/renovation-ledger-server
./gradlew test --tests com.renovation.ledger.server.ledger.ItemSyncServiceTest
```

Expected: `staleRevisionReturns409` 若仍在则旧行为；新测试 `sameItemLastWriteWins` FAIL（第二次 PUT 409）。

- [ ] **Step 3: 实现后写覆盖**

`ItemSyncService.upsert` / `delete` **删除** `baseRevision != ledger.revision` 的 409 判断。仍 `revision += 1` 仅作计数。重复删除已不存在的项：不报错，返回当前 snapshot。

`upsert` 核心：

```kotlin
val item = request.item.copy(id = itemId)
ledgerService.writeItem(ledgerId, item)
ledger.revision += 1
ledgers.save(ledger)
return ledgerService.snapshot(ledgerId)
```

- [ ] **Step 4: 再跑测试，期望 PASS**

Same command as Step 2. Expected: BUILD SUCCESSFUL, tests pass.

---

### Task 2: `POST /ledgers` 建空账本

**Files:**
- Modify: `LedgerDtos.kt` `LedgerService.kt` `LedgerController.kt`
- Test: `ItemSyncServiceTest.kt` 或新建 `LedgerCreateTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun createEmptyLedgerThenGet() {
    val token = login("create1")
    val json = mockMvc.post("/ledgers") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = mapper.writeValueAsString(CreateLedgerRequest(name = "新云账本", localId = "local_create1"))
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString
    val created = mapper.readValue<LedgerSnapshot>(json)
    assertEquals("新云账本", created.name)
    assertEquals(0, created.items.size)
    mockMvc.get("/ledgers") {
        header("Authorization", "Bearer $token")
    }.andExpect { status { isOk() } }
}
```

- [ ] **Step 2: 跑测试确认 404/405**

```bash
./gradlew test --tests com.renovation.ledger.server.ledger.LedgerCreateTest
```

Expected: FAIL（POST `/ledgers` 尚未映射；注意不要和 `/ledgers/import` 抢路径，空本映射必须是 `POST /ledgers` 且 **不能** 用会吃掉 import 的模糊路径）。

- [ ] **Step 3: 实现**

`CreateLedgerRequest(name: String, localId: String)`

`LedgerService.createLedger`：若已有 `(userId, localId)` 绑定则返回已有 snapshot（幂等）；否则 `LedgerEntity` + OWNER 成员 + import binding，taxonomy 空，items 空。

Controller：

```kotlin
@PostMapping("/ledgers")
fun create(@RequestBody request: CreateLedgerRequest): LedgerSnapshot =
    ledgerService.createLedger(request)
```

Spring 中更具体的 `/ledgers/import` 已存在，保持不变。

- [ ] **Step 4: 测试 PASS**

---

### Task 3: 绑手机改为微信取号

**Files:**
- Modify: `WeChatClient.kt` `AuthDtos.kt` `AuthService.kt`
- Test: `BindPhoneTest.kt`

当前 `BindPhoneRequest(phone, code)` 走短信 `app.dev-sms-code`。改为微信 `phoneCode`。

- [ ] **Step 1: 扩展 `WeChatClient` 并改测试**

```kotlin
fun interface WeChatClient {
    fun code2Session(code: String, client: String): WeChatSession
    fun phoneFromCode(phoneCode: String, client: String): String
}
```

Kotlin fun interface 只能一个方法。改成 **普通 interface**：

```kotlin
interface WeChatClient {
    fun code2Session(code: String, client: String): WeChatSession
    fun phoneFromCode(phoneCode: String, client: String): String
}
```

所有测试 stub 改为 object/匿名实现：`phoneFromCode` 返回 `"138" + phoneCode.takeLast(8)` 或固定映射：`phone_a` → `13800001111`，`phone_b` → `13800002222`。`code2Session` 保持 `oid_$code`。

`BindPhoneRequest`:

```kotlin
data class BindPhoneRequest(
    val phoneCode: String,
    val client: String = "mp",
)
```

`AuthResponse` 增加 `val phone: String? = null`。

测试：

```kotlin
content = mapper.writeValueAsString(BindPhoneRequest(phoneCode = "phone_a", client = "mp"))
```

两次绑定同一号（stub 对 `phone_a` 都返回 `13800001111`）→ 第二用户 409「该手机号已绑定其他账号」。

- [ ] **Step 2: 跑 `BindPhoneTest`，确认编译失败或 400**

- [ ] **Step 3: `AuthService.bindPhone`**

JWT 用户调用 `weChatClient.phoneFromCode(request.phoneCode, request.client)` 得到手机号，再走现有占用检查与写入。删掉 `request.code != devSmsCode`。

真实 `WeChatClient` 实现：小程序用微信 `getuserphonenumber`（access_token + phoneCode）；App 若暂无取号接口，实现里对 `client=="app"` 可先抛「请在微信小程序中绑定手机号」。测试 stub 两边都能返回号。

- [ ] **Step 4: 全量 `./gradlew test` PASS**

---

### Task 4: Android 同步仓库（列表 / 空本 / 后写覆盖）

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/remote/ApiModels.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/remote/LedgerApi.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/sync/LedgerSyncRepository.kt`
- Test: `app/src/test/java/com/renovation/ledger/LedgerSnapshotMapperTest.kt`（映射不动则加 `InviteShareText` 已有；本任务以仓库方法为主，可为 `resolve unbound` 抽纯函数单测）

- [ ] **Step 1: API 模型**

```kotlin
data class LedgerSummaryDto(
    val id: String,
    val name: String,
    val role: String,
    val revision: Long = 0,
)

data class CreateLedgerRequestDto(
    val name: String,
    val localId: String,
)

data class WeChatLoginRequestDto(
    val code: String,
    val client: String,
)

data class BindPhoneRequestDto(
    val phoneCode: String,
    val client: String,
)
```

`AuthResponseDto` 增加 `val phone: String? = null`。

`LedgerApi`：

```kotlin
@POST("auth/wechat")
suspend fun wechatLogin(@Body body: WeChatLoginRequestDto): AuthResponseDto

@POST("auth/bind-phone")
suspend fun bindPhone(
    @Header("Authorization") auth: String,
    @Body body: BindPhoneRequestDto,
): AuthResponseDto

@GET("ledgers")
suspend fun listLedgers(@Header("Authorization") auth: String): List<LedgerSummaryDto>

@POST("ledgers")
suspend fun createLedger(
    @Header("Authorization") auth: String,
    @Body body: CreateLedgerRequestDto,
): LedgerSnapshotDto
```

- [ ] **Step 2: `LedgerSyncRepository`**

新增：

- `suspend fun refreshOnOpen()`：有 JWT 则 `listLedgers`，把云端有、本地没有 `cloudLedgerId` 的账本建成占位 Project（name + cloudLedgerId + revision，items 空）；再 `pullCurrent()`。
- `suspend fun createCloudForCurrent()`：当前本无 `cloudLedgerId` 时 `createLedger(name, localId=project.id)` 再 `applySnapshot`。
- `pushItem`：409 不再 toast「该条已被其他人更新」再 throw `StaleSyncException`。成功则 `applySnapshot`；失败 toast + `markPending`。可保留发送 `baseRevision` 字段以免服务端 DTO 还要求该字段（服务端忽略比较即可）。

`importCurrent`：仅当无 `cloudLedgerId` 时 import；已绑定则 `pullCurrent`（给「我的」上传按钮用）。

- [ ] **Step 3: `./gradlew :app:testDebugUnitTest` 现有 mapper 测试仍 PASS**

---

### Task 5: Android「我的」+ 开发面板

**Files:**
- Modify: `ui/mine/MineScreen.kt` `MineViewModel.kt`
- Modify: `ui/debug/DebugCloudScreen.kt` `DebugCloudViewModel.kt`

- [ ] **Step 1: 开发面板去掉登录**

`DebugCloudScreen` 删除 jwt 文案、开发登录、退出登录。保留环境芯片、测通、地址。`DebugCloudViewModel.devLogin` 删除。测通继续 `pingDevLogin`（不写登录态）。

- [ ] **Step 2: 「我的」登录与上传**

未登录：`Button`「微信登录」；`BuildConfig.DEBUG` 时额外 `OutlinedButton`「开发登录」→ 现有 `devLogin()`。

已登录：邀请区保留。`上传当前账本` **仅当** `uiState.currentUnbound`（当前 project `cloudLedgerId` 为空）。去掉「从云端刷新」。增加未绑手机时「绑定手机号」（调微信；拿不到则 toast「请在微信小程序中绑定手机号」）。

`MineUiState` 增加 `currentUnbound: Boolean`、`phone: String?`。从 `observeProjectWithItems` + prefs 组合。

上传 loading/toast 保持现有 `withCloudBusy`。

- [ ] **Step 3: 微信登录先接 API 形状**

`wechatLogin(code)` 写入 jwt。本任务可用 Debug 的开发登录顶正式按钮；Task 7 接 SDK 后把「微信登录」接到真实 code。

---

### Task 6: Android 进 App 拉取、新建上云、CSV

**Files:**
- Modify: `ui/overview/OverviewViewModel.kt`
- Modify: `data/repo/ProjectRepository.kt` `createProject`
- Modify: `ui/importbatch/BatchImportConfirmViewModel.kt`

- [ ] **Step 1: 进总览 / 回前台**

`pullFromCloud()` 改为 `ledgerSync.refreshOnOpen()`。`switchProject` 成功后若已登录则 `pullCurrent()`（切过去那本）。

- [ ] **Step 2: 新建账本**

`createProject` 本地创建后：若 `jwt != null` 则 `createCloudForCurrent()`，失败 toast「云端创建失败，账本仍在本机」。

- [ ] **Step 3: CSV 已登录导入**

`confirmImport` 在 `createProjectForImport` + `upsertItems` 之后：若有 jwt，调用 `importCurrent()`（新 localId，走 import）。失败 toast，本地新账本保留。未登录不调接口。

- [ ] **Step 4: `sh oneClickSetup`**（本任务改完 Android 行为后，若设备在线）

```bash
cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup
```

Expected: BUILD SUCCESSFUL；有设备则 install Success。

---

### Task 7: Android 微信开放平台登录

**Files:**
- Modify: `app/build.gradle.kts`（`implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.24")` 或工程已有版本）
- Create: `app/src/main/java/com/renovation/ledger/wxapi/WXEntryActivity.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/auth/WeChatAppAuth.kt`
- Modify: `AndroidManifest.xml`（`WXEntryActivity` exported、scheme）
- Modify: `MineViewModel` 微信登录按钮

- [ ] **Step 1: AppId**

`local.properties` 增加 `WECHAT_APP_ID=...`（不要写进 git）。`build.gradle.kts` `buildConfigField("WECHAT_APP_ID", ...)`。未配置时点击微信登录 toast「未配置微信 AppId」。

- [ ] **Step 2: 发起授权**

`WeChatAppAuth.sendAuth(activity)`：`SendAuth.Req` scope `snsapi_userinfo`（手机号若开放平台提供再加对应 scope）。`WXEntryActivity` 收到 code → `ledgerSync.wechatLogin(code, client="app")`。

- [ ] **Step 3: 绑手机**

授权回调若无手机号：不拦登录。「我的」绑定按钮再次 `sendAuth`；仍无号则 toast「请在微信小程序中绑定手机号」。有 `phoneCode` 则 `bindPhone`。

- [ ] **Step 4: 正式包没有开发登录**（`if (BuildConfig.DEBUG)`）

- [ ] **Step 5: `sh oneClickSetup`**

---

### Task 8: 小程序同步与界面（对等 Task 4–6）

**Files:**
- Modify: `utils/sync.js` `utils/api.js`
- Modify: `pages/mine/mine.js` `mine.wxml`
- Modify: `pages/debug/debug.js` `debug.wxml`
- Modify: `pages/overview/overview.js` `utils/store.js`

- [ ] **Step 1: `sync.js`**

- `refreshOnOpen`：jwt 则 `GET /ledgers`，缺的云账本 `store` 里补占位 project；再 `pull()` 当前本。
- `createCloudForCurrent`：`POST /ledgers` `{ name, localId }`。
- `pushItem`：去掉 409 则 pull 覆盖本地的分支；失败 toast + pending。
- `wechatLogin(code)` `bindPhone(phoneCode)`。

- [ ] **Step 2: 开发面板去掉登录/退出**

与 Android 一样只留环境。

- [ ] **Step 3: 「我的」**

未登录：微信登录按钮（`wx.login` 后 `wechatLogin`）。`envVersion === 'develop'` 时显示开发登录。已登录：未绑定才显示上传；去掉从云端刷新；邀请保留。

- [ ] **Step 4: overview `onShow`**

`refreshOnOpen()`。新建账本：`store.createProject` 后若 jwt 则 `createCloudForCurrent`。`store.createProjectForImport` 确认导入后若 jwt 则 `importCurrent`。

---

### Task 9: 小程序微信登录 + 手机号快速验证

**Files:**
- Modify: `pages/mine/mine.wxml` `mine.js`

- [ ] **Step 1: 微信登录**

```javascript
wx.login({
  success: async (res) => {
    await require('../../utils/sync').wechatLogin(res.code)
    this.refresh()
  },
})
```

- [ ] **Step 2: 绑手机**

未绑时用开放能力按钮：

```xml
<button wx:if="{{jwt && !phone}}" open-type="getPhoneNumber" bindgetphonenumber="onGetPhoneNumber">绑定手机号</button>
```

```javascript
async onGetPhoneNumber(e) {
  const code = e.detail && e.detail.code
  if (!code) {
    wx.showToast({ title: '未授权手机号', icon: 'none' })
    return
  }
  await require('../../utils/sync').bindPhone(code)
}
```

- [ ] **Step 3: 开发者工具手工点一遍登录 / 取号（真机）**

---

### Task 10: 手工验收

- [ ] **Step 1: 服务端 local profile 运行**

```bash
cd /Users/beike/Projects/renovation-ledger-server
./gradlew bootRun --args='--spring.profiles.active=local --server.address=0.0.0.0'
```

- [ ] **Step 2: 清单**

1. Debug 摇一摇：只能切环境，没有登录。  
2. 「我的」未登录：微信登录；Debug 另有开发登录。  
3. 登录后进总览：列表出现云账本；当前本数据来自服务器。  
4. 未绑云的本机账本才有上传；上传成功按钮消失。  
5. 登录后新建账本：服务器 `GET /ledgers` 能看到。  
6. 已登录 CSV 导入：新账本且已绑云。  
7. 导出仍只出当前本文件。  
8. 两人改不同项，各自再进页能看到对方已成功的写入。  
9. 两人改同一条：后保存的覆盖。  
10. 小程序取号成功；Android 无号时能用账本，提示去小程序绑。  
11. 邀请加入后无上传按钮。  

- [ ] **Step 3: Android `sh oneClickSetup` + `adb reverse tcp:18080 tcp:8080`**

---

## Spec coverage

| Spec 条款 | 任务 |
|-----------|------|
| 开发面板只切环境 | 5, 8 |
| 「我的」微信登录 / Debug 开发登录 | 5, 7, 8, 9 |
| 进 App 列表+当前本 | 4, 6, 8 |
| 写后自动推、同条后写覆盖 | 1, 4, 8 |
| 未绑定才上传 | 5, 8 |
| 登录后新建立刻上云 | 2, 6, 8 |
| CSV 已登录新本上云；导出不变 | 6, 8 |
| 微信正式登录 + 绑手机 | 3, 7, 9 |
| 不做 409 协作 / CRDT | 1 |

## 配置（实现时填写，勿把密钥写入仓库）

- 小程序 AppId / Secret → server `application.yml` `app.wechat.*`  
- Android 开放平台 AppId / 签名 → `local.properties` `WECHAT_APP_ID`  
- 微信后台配置服务器域名与业务域名  
