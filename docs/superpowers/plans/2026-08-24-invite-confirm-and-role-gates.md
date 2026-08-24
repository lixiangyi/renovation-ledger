# 邀请确认、成员列表与角色门控 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 邀请加入前确认弹窗；成员列表跟云端且含自己；去掉成员内改昵称与本地加人；仅 OWNER 可生成邀请码/见健康色编辑；删除弹窗按 OWNER/EDITOR 分文案。

**Architecture:** 服务端新增 `GET /invites/{code}/preview`；客户端用 `GET /ledgers/{id}/members` 展示成员，用 `GET /ledgers` 的 `role` 做 UI 门控；邀请确认在个人中心编排 preview→dialog→join。

**Tech Stack:** Spring Boot (server)、Kotlin/Compose (Android)、微信小程序 JS。

**约束：** 禁止 git commit（用户级 no-git-operations）；Android/小程序/服务端三仓同步改。

---

## File map

| 区域 | 文件 |
|------|------|
| Server | `LedgerDtos.kt`, `InviteService.kt`, `InviteController.kt`, `InviteServiceTest.kt` |
| Android domain | `DeleteLedgerCopy.kt`（新建）, `LedgerRoleGates.kt`（新建） |
| Android data | `ApiModels.kt`, `LedgerApi.kt`, `LedgerSyncRepository.kt` |
| Android UI | `ProfileViewModel.kt`, `ProfileScreen.kt`, `MineViewModel.kt`, `MineScreen.kt`, `OverviewScreen.kt` |
| MP | `utils/sync.js`, `pages/profile/*`, `pages/mine/*`, `pages/overview/overview.js` |

### Task 1: Server invite preview

**Files:**
- Modify: `renovation-ledger-server/src/main/kotlin/.../LedgerDtos.kt`
- Modify: `.../InviteService.kt`
- Modify: `.../InviteController.kt`
- Modify: `.../InviteServiceTest.kt`

- [x] **Step 1: 写失败测试 `previewReturnsOwnerAndLedgerWithoutJoining`**

在 `InviteServiceTest` 增加：

```kotlin
@Test
fun previewReturnsOwnerAndLedgerWithoutJoining() {
    val owner = login("inv_prev_o")
    val editor = login("inv_prev_e")
    val ledger = import(owner, "p_prev")
    // 先改 owner 昵称（若有 PATCH /me）；否则默认昵称也可断言非空
    val invite = createInvite(owner, ledger.id)
    val json = mockMvc.get("/invites/${invite.code}/preview") {
        header("Authorization", "Bearer $editor")
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString
    val preview: InvitePreviewDto = mapper.readValue(json)
    assertEquals(invite.code, preview.code)
    assertEquals(ledger.id, preview.ledgerId)
    assertEquals(ledger.name, preview.ledgerName)
    assertTrue(preview.ownerNickname.isNotBlank())
    assertEquals(false, preview.alreadyMember)
    val membersBeforeJoin: List<MemberDto> = mapper.readValue(
        mockMvc.get("/ledgers/${ledger.id}/members") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )
    assertEquals(1, membersBeforeJoin.size)
}

@Test
fun previewExpiredIs410() {
    val owner = login("inv_prev_exp_o")
    val editor = login("inv_prev_exp_e")
    val ledger = import(owner, "p_prev_exp")
    val invite = createInvite(owner, ledger.id)
    val entity = invites.findByCode(invite.code)!!
    entity.expiresAt = java.time.Instant.now().minusSeconds(60)
    invites.save(entity)
    mockMvc.get("/invites/${invite.code}/preview") {
        header("Authorization", "Bearer $editor")
    }.andExpect { status { isGone() } }
}
```

- [x] **Step 2: 跑测试确认失败（404 / 编译失败）**

Run: `cd ~/Projects/renovation-ledger-server && ./gradlew test --tests '*InviteServiceTest*preview*'`

- [x] **Step 3: 实现 DTO + preview**

```kotlin
data class InvitePreviewDto(
    val code: String,
    val ledgerId: String,
    val ledgerName: String,
    val ownerNickname: String,
    val alreadyMember: Boolean,
)
```

`InviteService.preview(code)`：校验同 join；查 ledger name；OWNER 成员 nickname（缺省 `"账本拥有者"`）；`alreadyMember`；不写 members。

`InviteController`: `@GetMapping("/invites/{code}/preview")`

- [x] **Step 4: 跑测试通过**

---

### Task 2: Android 纯函数 — 角色门控与删除文案

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/ledger/LedgerRoleGates.kt`
- Create: `app/src/main/java/com/renovation/ledger/domain/ledger/DeleteLedgerCopy.kt`
- Create: `app/src/test/java/com/renovation/ledger/LedgerRoleGatesTest.kt`
- Create: `app/src/test/java/com/renovation/ledger/DeleteLedgerCopyTest.kt`

- [x] **Step 1: 失败测试**

```kotlin
// LedgerRoleGates
fun canManageInviteAndHealth(role: String?, loggedIn: Boolean, hasCloudId: Boolean): Boolean
// OWNER 或（未登录/无 cloud）→ true；EDITOR → false

// DeleteLedgerCopy
data class DeleteLedgerDialogCopy(val title: String, val body: String, val confirm: String)
fun forRole(role: String?, ledgerName: String, hasCloudId: Boolean): DeleteLedgerDialogCopy
```

EDITOR → 解绑；OWNER+cloud → 删除云端；无 cloud → 删除本地文案。

- [x] **Step 2: 实现至测试通过**

Run: `./gradlew :app:testDebugUnitTest --tests '*LedgerRoleGates*' --tests '*DeleteLedgerCopy*'`

---

### Task 3: Android API — preview + listMembers

**Files:**
- Modify: `ApiModels.kt`, `LedgerApi.kt`, `LedgerSyncRepository.kt`

- [x] **Step 1: 增加 DTO 与 API**

```kotlin
data class InvitePreviewDto(
    val code: String,
    val ledgerId: String,
    val ledgerName: String,
    val ownerNickname: String,
    val alreadyMember: Boolean = false,
)
data class MemberDto(val userId: String, val nickname: String, val role: String)

@GET("invites/{code}/preview")
suspend fun previewInvite(...): InvitePreviewDto

@GET("ledgers/{id}/members")
suspend fun listMembers(...): List<MemberDto>
```

- [x] **Step 2: Sync 方法** `previewInvite(code)`, `listMembers(cloudId)`；join 成功后可调用 listMembers（由 UI 触发刷新）

---

### Task 4: Android 个人中心 — 邀请确认 + OWNER 才生成码

**Files:** `ProfileViewModel.kt`, `ProfileScreen.kt`

- [x] **Step 1:** `uiState` 增加 `isOwner: Boolean`（当前本 cloudId 在 summaries 中 role != EDITOR；无 cloud 视为 true 但不显示生成码因 unbound）
- [x] **Step 2:** `requestJoinInvite(code)` → preview → 暴露 `pendingJoinConfirm: InvitePreviewDto?`；`confirmJoin` / `cancelJoin`
- [x] **Step 3:** UI 弹窗文案；仅 `isOwner && !currentUnbound` 显示「生成邀请码」

---

### Task 5: Android 「我的」— 云端成员、去改昵称/加人、健康色门控、删除文案

**Files:** `MineViewModel.kt`, `MineScreen.kt`, `OverviewScreen.kt`（及 OverviewViewModel 若需 role）

- [x] **Step 1:** 成员：有 cloud 时 `listMembers` → `cloudMembers`；展示含自己；去掉改昵称与添加成员
- [x] **Step 2:** `showHealthColorSettings = LedgerRoleGates.canManageInviteAndHealth(...)`
- [x] **Step 3:** 删除弹窗用 `DeleteLedgerCopy.forRole`；Overview 抽屉同步

---

### Task 6: 小程序对等

**Files:** `utils/sync.js`, `pages/profile/*`, `pages/mine/*`, `pages/overview/overview.js`

- [x] preview + listMembers 导出
- [x] profile：join 前 showModal；仅 OWNER 生成邀请码
- [x] mine：云端成员列表；去加人/改昵称；EDITOR 隐藏健康色；删除/解绑文案
- [x] overview 删除入口对齐

---

### Task 7: 验证

- [x] Server: `./gradlew test --tests '*InviteServiceTest*'`
- [x] Android unit: gates + delete copy tests
- [x] `sh oneClickSetup`（Android 仓库根，偏好默认要）

---

## Spec coverage

| Spec | Task |
|------|------|
| §3 preview + 确认弹窗 | 1, 4, 6 |
| §4 云端成员 / 去掉加人改昵称 | 3, 5, 6 |
| §5 角色门控 | 2, 4, 5, 6 |
| §6 删除/解绑文案 | 2, 5, 6 |
