# 云同步后台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 spec `docs/superpowers/specs/2026-08-13-cloud-sync-server-design.md` 落地自建 API（微信登录、账本同步、邀请码多人编辑），Android 与小程序打开/下拉时互通。

**Architecture:** 新仓库 Spring Boot + Kotlin + JPA 访问 Postgres。账本 `revision` + 项 `updatedAt` 做乐观锁，冲突返回 409。客户端 Room / `wx.setStorageSync` 仍作展示缓存；JWT 放本地。微信 HTTP 用接口隔离，测试用替身。

**Tech Stack:** Java 17、Spring Boot 3.4、Kotlin、Spring Web/Security/Data JPA、PostgreSQL、JJWT；Android OkHttp/Moshi + DataStore；小程序 `wx.request`；Android 收尾 `sh oneClickSetup`。

**Git：** 工作区禁止自动 git。计划中 **不** 执行 commit；仅当用户明确要求时再提交。新建 server 仓库也不要擅自 `git init` / `git remote`，除非用户点名。

**Spec 分期：** S0 登录 → S1 单用户同步 → S2 邀请成员 → S3 手机号 + 冲突/离线 UI。

---

## File map

### 新建 `/Users/beike/Projects/renovation-ledger-server`

| 路径 | 职责 |
|------|------|
| `build.gradle.kts` / `settings.gradle.kts` / `src/main/resources/application.yml` | 工程与配置 |
| `src/main/kotlin/com/renovation/ledger/server/LedgerServerApplication.kt` | 入口 |
| `config/SecurityConfig.kt` `config/JwtService.kt` `config/JwtAuthFilter.kt` | JWT |
| `wechat/WeChatClient.kt` `wechat/WeChatProperties.kt` | 微信 code2session（可 mock） |
| `user/UserEntity.kt` `user/UserIdentityEntity.kt` `user/UserRepository.kt` | 用户 |
| `auth/AuthController.kt` `auth/AuthService.kt` `auth/AuthDtos.kt` | 登录 / 绑手机 |
| `ledger/LedgerEntity.kt` `ledger/LedgerMemberEntity.kt` `ledger/BudgetItemEntity.kt` `ledger/PaymentEntity.kt` `ledger/LedgerTaxonomyEntity.kt` `ledger/InviteEntity.kt` | 表 |
| `ledger/LedgerService.kt` `ledger/ItemSyncService.kt` `ledger/InviteService.kt` `ledger/LedgerController.kt` `ledger/InviteController.kt` | 业务与 HTTP |
| `error/ApiException.kt` `error/ApiExceptionHandler.kt` | 401/403/409/410 |
| `src/test/kotlin/.../AuthServiceTest.kt` `ItemSyncServiceTest.kt` `InviteServiceTest.kt` `LedgerAccessTest.kt` | 单测 / MockMvc |

### 修改 Android `~/Projects/renovation-ledger`

| 路径 | 改动 |
|------|------|
| `app/build.gradle.kts` | OkHttp + Moshi |
| `data/remote/*` | API 模型、AuthApi、LedgerApi、Retrofit |
| `data/prefs/UserPrefs.kt` | jwt、cloudUserId |
| `data/local/entity/ProjectEntity.kt` | `cloudLedgerId`、`pendingSync` |
| `data/local/AppDatabase.kt` | migration |
| `data/sync/LedgerSync.kt` | 拉整本 / 推项 / 409 |
| `ui/mine` 或 overview 抽屉 / 设置 | 微信登录、上传、邀请 |

### 修改小程序 `~/Projects/renovation-ledger-miniprogram`

| 路径 | 改动 |
|------|------|
| `utils/api.js` `utils/auth.js` | request + token |
| `utils/store.js` | `cloudLedgerId`、jwt |
| `pages/mine/*` | 登录、上传、邀请码 |
| `app.js` | 启动不强制登录 |

---

## S0 · 服务端工程 + 微信登录

### Task 1: 脚手架 Spring Boot Kotlin

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-server/settings.gradle.kts`
- Create: `/Users/beike/Projects/renovation-ledger-server/build.gradle.kts`
- Create: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/LedgerServerApplication.kt`
- Create: `/Users/beike/Projects/renovation-ledger-server/src/main/resources/application.yml`
- Create: `/Users/beike/Projects/renovation-ledger-server/src/test/kotlin/com/renovation/ledger/server/ContextLoadTest.kt`

- [ ] **Step 1: 建目录与 Gradle 文件**

`settings.gradle.kts`:

```kotlin
rootProject.name = "renovation-ledger-server"
```

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.renovation.ledger"
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> { useJUnitPlatform() }
```

`LedgerServerApplication.kt`:

```kotlin
package com.renovation.ledger.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LedgerServerApplication

fun main(args: Array<String>) {
    runApplication<LedgerServerApplication>(*args)
}
```

`application.yml`:

```yaml
server.port: 8080
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/renovation_ledger
    username: ledger
    password: ledger
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
app:
  jwt-secret: "change-me-to-32-bytes-min-secret-key!!"
  jwt-days: 30
  wechat:
    mp-app-id: ${WECHAT_MP_APP_ID:}
    mp-secret: ${WECHAT_MP_SECRET:}
    app-app-id: ${WECHAT_APP_APP_ID:}
    app-secret: ${WECHAT_APP_SECRET:}
```

测试用 `src/test/resources/application.yml` 覆盖为 H2：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:ledger;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa.hibernate.ddl-auto: create-drop
app.jwt-secret: "test-secret-key-which-is-at-least-32b"
```

- [ ] **Step 2: 写启动冒烟测试**

```kotlin
package com.renovation.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContextLoadTest {
    @Test
    fun contextLoads() {}
}
```

- [ ] **Step 3: 跑测试确认工程能起来**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.ContextLoadTest`

若还没有 wrapper：`gradle wrapper --gradle-version 8.12` 后再跑。

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit** — 跳过（用户未要求 git）

---

### Task 2: JwtService + 用户表 + 微信登录（TDD）

**Files:**
- Create: `.../config/JwtService.kt`
- Create: `.../wechat/WeChatClient.kt`
- Create: `.../user/UserEntity.kt` `UserIdentityEntity.kt` `UserRepository.kt`
- Create: `.../auth/AuthService.kt` `AuthController.kt` `AuthDtos.kt`
- Create: `.../error/ApiException.kt` `ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/renovation/ledger/server/auth/AuthServiceTest.kt`

- [ ] **Step 1: 写失败测试（openid 已存在则发同一 user 的 JWT）**

```kotlin
package com.renovation.ledger.server.auth

import com.renovation.ledger.server.wechat.WeChatClient
import com.renovation.ledger.server.wechat.WeChatSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootTest
class AuthServiceTest {
    @TestConfiguration
    class WeChatStubConfig {
        @Bean
        @Primary
        fun weChatClient(): WeChatClient = WeChatClient { _, _ ->
            WeChatSession(openid = "mp_openid_1", unionid = "union_1")
        }
    }

    @Autowired lateinit var authService: AuthService

    @Test
    fun wechatLoginTwiceSameOpenidSameUser() {
        val first = authService.loginWeChat(WeChatLoginRequest(code = "c1", client = "mp"))
        val second = authService.loginWeChat(WeChatLoginRequest(code = "c2", client = "mp"))
        assertEquals(first.userId, second.userId)
        assertNotNull(first.token)
    }
}
```

Expected before impl: compile fail `AuthService` unresolved.

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew test --tests com.renovation.ledger.server.auth.AuthServiceTest`

Expected: FAIL（类不存在）

- [ ] **Step 3: 最小实现**

`WeChatClient.kt`:

```kotlin
package com.renovation.ledger.server.wechat

data class WeChatSession(val openid: String, val unionid: String?)

fun interface WeChatClient {
    fun code2Session(code: String, client: String): WeChatSession
}
```

生产实现调 `https://api.weixin.qq.com/sns/jscode2session`（`client=="mp"`）或开放平台 `sns/oauth2/access_token`（`client=="app"`）。测试用上面的 `@Primary` stub。

`UserEntity.kt` / `UserIdentityEntity.kt`：

```kotlin
package com.renovation.ledger.server.user

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id val id: String = UUID.randomUUID().toString(),
    var nickname: String = "我",
    var avatarUrl: String? = null,
    @Column(unique = true) var phone: String? = null,
)

@Entity
@Table(
    name = "user_identities",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "openid"])],
)
class UserIdentityEntity(
    @Id val id: String = UUID.randomUUID().toString(),
    var userId: String,
    var provider: String, // wechat_mp | wechat_app | google | apple
    var openid: String,
    var unionid: String? = null,
)
```

`AuthService.loginWeChat` 逻辑：

1. `weChatClient.code2Session(code, client)`
2. provider = `wechat_mp` 或 `wechat_app`
3. 按 `(provider, openid)` 找 identity；没有则按 `unionid` 找已有 identity 的 userId
4. 仍没有则新建 `UserEntity` + identity
5. `jwtService.create(userId)` 返回 `AuthResponse(userId, token, nickname)`

`JwtService`：HS256，claim `sub` = userId，过期 `app.jwt-days`（30）。

`POST /auth/wechat` body：`{ "code": "...", "client": "mp"|"app" }`，此接口 **permitAll**。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew test --tests com.renovation.ledger.server.auth.AuthServiceTest`

Expected: PASS

- [ ] **Step 5: Commit** — 跳过

---

### Task 3: JWT 过滤器

**Files:**
- Create: `config/JwtAuthFilter.kt` `config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/renovation/ledger/server/auth/JwtFilterTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.renovation.ledger.server.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class JwtFilterTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun ledgersWithoutTokenIs401() {
        mockMvc.get("/ledgers").andExpect { status { isUnauthorized() } }
    }
}
```

先加一个空的 `GET /ledgers` 需认证，测试才会有意义。

- [ ] **Step 2: 跑测试确认失败或 404**

Run: `./gradlew test --tests com.renovation.ledger.server.auth.JwtFilterTest`

Expected: FAIL（404 或 403 而非 401）直到 Filter + Controller 就绪

- [ ] **Step 3: 实现**

`SecurityConfig`：`/auth/wechat`、`/error` permitAll；其余 authenticated。禁用 CSRF（纯 API）。Session STATELESS。

`JwtAuthFilter`：读 `Authorization: Bearer`，校验后 `SecurityContext` 的 name = userId。

临时 `LedgerController`：

```kotlin
@RestController
class LedgerController {
    @GetMapping("/ledgers")
    fun list() = emptyList<Any>()
}
```

- [ ] **Step 4: 跑测试确认通过**

Expected: PASS（401）

- [ ] **Step 5: Commit** — 跳过

---

## S1 · 账本 CRUD + 导入 + 项同步

### Task 4: 账本 / 项 / 付款 / 标签实体

**Files:**
- Create: `ledger/LedgerEntity.kt` `LedgerMemberEntity.kt` `BudgetItemRow.kt` `PaymentRow.kt` `LedgerTaxonomyEntity.kt` 及对应 Repository

实体字段必须与 spec §10 和 Android Room 对齐：

`ledgers`: `id`, `name`, `revision` (Long, default 0), `ownerUserId`, `deletedAt` (Instant?)

`ledger_members`: `id`, `ledgerId`, `userId`, `role` (`OWNER`|`EDITOR`)

`budget_items`: `id`, `ledgerId`, `name`, `stage`, `category`, `space`, `budgetAmount`, `contractAmount`, `merchant`, `recordedDate`, `remark`, `isNewAddition`, `updatedAt` (Instant)

`payments`: `id`, `budgetItemId`, `type`, `amount`, `status`, `paidAtEpochMs`, `note`, `receiptUri`, `createdByUserId`, `createdByName`

`ledger_taxonomy`: `ledgerId` PK, `stagesJson`, `categoriesJson`, `spacesJson`, `iconsJson`（字符串，内容与现有 TaxonomyPrefs JSON 同形）

- [ ] **Step 1: 无独立测试也可** — 下一 Task 的 import 测试会创建这些表

- [ ] **Step 2: 实现实体与 Repository**（`JpaRepository<LedgerEntity, String>` 等）

- [ ] **Step 3: Commit** — 跳过

---

### Task 5: import + GET 整本 + 列表（TDD）

**Files:**
- Modify: `ledger/LedgerService.kt` `ledger/LedgerController.kt` `ledger/LedgerDtos.kt`
- Test: `src/test/kotlin/com/renovation/ledger/server/ledger/LedgerImportTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun importThenGetRoundTrip() {
    val token = login()
    val body = ImportLedgerRequest(
        localId = "proj_1",
        name = "我家装修",
        items = listOf(
            ItemDto(
                id = "item_1",
                name = "灯具",
                stage = "软装",
                category = "灯具",
                space = "客厅",
                budgetAmount = 10000,
                contractAmount = null,
                merchant = "",
                recordedDate = null,
                remark = "",
                isNewAddition = false,
                payments = emptyList(),
            ),
        ),
        taxonomy = TaxonomyDto(stages = listOf("软装"), categories = listOf("灯具"), spaces = listOf("客厅"), iconsJson = "{}"),
    )
    val created = import(token, body)
    val fetched = getLedger(token, created.id)
    assertEquals("我家装修", fetched.name)
    assertEquals(1, fetched.items.size)
    assertEquals("灯具", fetched.items[0].name)
}

@Test
fun importSameLocalIdTwiceDoesNotDuplicate() {
    val token = login()
    val a = import(token, sample("proj_1"))
    val b = import(token, sample("proj_1"))
    assertEquals(a.id, b.id)
    assertEquals(1, listLedgers(token).size)
}
```

客户端约定：`ImportLedgerRequest.localId` 存到成员表或单独 `ledger_client_bindings(userId, localId, ledgerId)` 防重复。**采用绑定表** `ledger_import_bindings(userId, localProjectId, ledgerId)` unique(userId, localProjectId)。

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现**

`POST /ledgers/import`：创建 ledger revision=0，当前用户 OWNER，写入 items/payments/taxonomy，写 binding，返回 `GET` 同结构的 `LedgerSnapshot`。

`GET /ledgers`：当前用户未删除成员关系的账本摘要 `{ id, name, role, revision }`。

`GET /ledgers/{id}`：须为成员且 `deletedAt == null`，否则 403。返回 snapshot（含 items[].payments、taxonomy、revision）。

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: Commit** — 跳过

---

### Task 6: PUT 项 + revision 冲突 409（TDD）

**Files:**
- Create: `ledger/ItemSyncService.kt`
- Modify: `LedgerController.kt`
- Test: `src/test/kotlin/com/renovation/ledger/server/ledger/ItemSyncServiceTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun putItemIncrementsLedgerRevision() {
    val token = login()
    val ledger = import(token, sample())
    putItem(token, ledger.id, itemCopy(ledger.items[0], name = "灯具-改"), baseRevision = ledger.revision)
    val again = getLedger(token, ledger.id)
    assertEquals("灯具-改", again.items[0].name)
    assertEquals(ledger.revision + 1, again.revision)
}

@Test
fun staleRevisionReturns409() {
    val token = login()
    val ledger = import(token, sample())
    putItem(token, ledger.id, itemCopy(ledger.items[0], name = "A"), baseRevision = ledger.revision)
    mockMvc.put("/ledgers/${ledger.id}/items/${ledger.items[0].id}") {
        header("Authorization", "Bearer $token")
        contentType = APPLICATION_JSON
        content = json(itemCopy(ledger.items[0], name = "B"), baseRevision = ledger.revision) // 旧 revision
    }.andExpect { status { isConflict() } }
}
```

PUT body：`{ "baseRevision": 0, "item": { ...ItemDto including payments } }`。

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现**

`ItemSyncService.upsert` 在事务中：

1. 锁 ledger 行（`findByIdForUpdate`）
2. 校验成员且角色可写（OWNER 与 EDITOR 均可）
3. 若 `request.baseRevision != ledger.revision` → throw `ApiException(409, "STALE", "该条已被其他人更新，请刷新")`
4. upsert item + 替换该 item 的 payments（先删后插或按 id 同步）
5. `ledger.revision += 1`，`item.updatedAt = now`
6. 返回最新 snapshot 或该项 + newRevision

`DELETE /ledgers/{id}/items/{itemId}`：同样带 query `baseRevision`；项已不存在视为成功；revision 不匹配 409；成功则 revision+1。

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: Commit** — 跳过

---

### Task 7: 非成员 403

**Files:**
- Test: `LedgerAccessTest.kt`

- [ ] **Step 1: 写失败测试** — 用户 B 的 token 去 GET/PUT 用户 A 的 ledger → 403

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: `LedgerAccess.requireMember(userId, ledgerId)`** 无记录或 ledger 软删 → `ApiException(403, "FORBIDDEN", "没有这个账本的权限")`

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: Commit** — 跳过

---

## S2 · 邀请与成员

### Task 8: 邀请码生成 / 加入 / 过期（TDD）

**Files:**
- Create: `ledger/InviteEntity.kt` `InviteService.kt` `InviteController.kt`
- Test: `InviteServiceTest.kt`

短码：6 位大写字母数字，排除易混 `0O1I`。`expiresAt = now + 7 days`。`revokedAt` 可空。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun ownerCreatesInviteEditorJoins() {
    val owner = loginAs("owner")
    val editor = loginAs("editor")
    val ledger = import(owner, sample())
    val invite = createInvite(owner, ledger.id) // POST /ledgers/{id}/invites
    join(editor, invite.code) // POST /invites/join { "code": "..." }
    val members = listMembers(owner, ledger.id)
    assertEquals(2, members.size)
    assertTrue(members.any { it.role == "EDITOR" })
}

@Test
fun expiredInviteIs410() { /* 把 expiresAt 设为过去 */ }

@Test
fun editorCannotCreateInvite() { /* 403 */ }

@Test
fun joinTwiceIsIdempotent() { /* 仍 2 人 */ }
```

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现**

`POST /ledgers/{id}/invites` 仅 OWNER。

`DELETE /ledgers/{id}/invites/{inviteId}` 设 `revokedAt=now`。

`POST /invites/join`：code 找不到或过期或已作废 → 410「邀请已失效」；已是成员 → 200 原样；否则插入 EDITOR。

- [ ] **Step 4: 跑测试确认通过**

- [ ] **Step 5: Commit** — 跳过

---

### Task 9: 踢人、退出、转让、软删除

**Files:**
- Modify: `LedgerService.kt` `LedgerController.kt`
- Test: `MemberLifecycleTest.kt`

- [ ] **Step 1: 写失败测试**

- 踢人：OWNER `DELETE /ledgers/{id}/members/{userId}`，被踢者再 GET → 403
- 不能踢 OWNER
- `POST /ledgers/{id}/leave`：EDITOR 退出；OWNER leave → 400「请先转让」
- `POST /ledgers/{id}/transfer` body `{ "userId": editorId }`：仅 OWNER；对方须已是成员；然后角色对调
- `DELETE /ledgers/{id}`：仅 OWNER，设 `deletedAt`；成员 GET → 403
- `POST /ledgers/{id}/restore`：仅 OWNER 且 deletedAt 在 30 天内

- [ ] **Step 2–4: 实现并跑通测试**

- [ ] **Step 5: Commit** — 跳过

---

## S3 · 手机号 + 双端客户端

### Task 10: 绑定手机号

**Files:**
- Modify: `AuthService.kt` `AuthController.kt`
- Test: `BindPhoneTest.kt`

第一期校验码：**测试与开发**用固定 `app.dev-sms-code: "000000"`（yml）；生产再接短信。请求 `POST /auth/bind-phone` `{ "phone": "13800000000", "code": "000000" }`。

规则：

- phone 已被其他 user 占用 → 409「该手机号已绑定其他账号」
- 当前 user 已绑同一号 → 200
- 绑成功后：若存在另一 user 仅通过不同微信渠道且 **尚未绑号** 的冲突，**第一期不自动合并**（spec：不做两账号合并）。仅写当前 user.phone。
- 若当前 App 登录用户与已绑此手机号的用户是不同人 → 409（不合并）

额外：同一 `unionid` 在 Task 2 已合并，绑号主要解决「无 unionid 的 mp/app 两号」。**第一期绑号不合并两个 User**，只拒绝占用。无 unionid 的双端两账号，用户需知道会是两个人——在「我的」文案写「建议绑定手机号以便找回」。后续版本再做合并。**按 spec 不做手动合并，本任务不实现合并。**

- [ ] **Step 1–4: 测试占用 409 + 成功绑定；实现；跑通**

- [ ] **Step 5: Commit** — 跳过

---

### Task 11: Android 网络层 + Token + 同步核心（TDD 映射）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/remote/LedgerApi.kt` `ApiModels.kt` `AuthApi.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/sync/LedgerSnapshotMapper.kt`
- Create: `app/src/test/java/com/renovation/ledger/LedgerSnapshotMapperTest.kt`
- Modify: `app/build.gradle.kts`（okhttp、moshi、retrofit）
- Modify: `UserPrefs.kt` 增加 `jwt`、`cloudUserId`
- Modify: `ProjectEntity.kt` 增加 `cloudLedgerId: String? = null`；Room version +1 migration
- Create: `data/sync/LedgerSyncRepository.kt`

`ApiModels` 字段名与服务端 DTO 一致（camelCase）。

- [ ] **Step 1: 写 Mapper 测试** — Room `BudgetItem`+`Payment` 列表 ↔ `ItemDto` 往返相等（id/金额分/付款列表）

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 实现 Mapper + Retrofit `baseUrl` 先读 `BuildConfig` 或 DataStore `serverBaseUrl` 默认 `http://10.0.2.2:8080/`（模拟器）**

`LedgerSyncRepository`：

- `importCurrentProject()`：当前 Room 账本 → `POST /ledgers/import` → 写 `cloudLedgerId`
- `pull(projectId)`：`GET /ledgers/{cloudId}` 覆盖该 project 的 items/payments（事务）
- `pushItem(itemId)`：`PUT` 带 `baseRevision`（存在 UserPrefs 或 Project 新列 `cloudRevision: Long`）
- 409：解析 body，用服务器 snapshot 覆盖该项所属账本，向上抛 `StaleSyncException`

`ProjectEntity` 增加 `cloudRevision: Long = 0`。

- [ ] **Step 4: 跑 Mapper 测试通过**；`./gradlew test` 相关类

- [ ] **Step 5: Commit** — 跳过

---

### Task 12: Android 登录 / 上传 / 邀请 UI

**Files:**
- Modify: `ui/overview/OverviewScreen.kt` 抽屉增加「登录」「上传到云」「邀请家人」
- Create: `ui/cloud/CloudAccountViewModel.kt`（或挂现有 VM）
- 微信 Android SDK：第一期可用 **开发旁路** `POST /auth/wechat` 在 debug 包走 `WeChatClient` 的测试码——服务端增加 **仅 `app.dev-login-token: true` 时** `POST /auth/dev-login` `{ "label": "dev" }` 创建用户。生产关闭。这样无开放平台也能联调。真微信 SDK 作为后续补丁，不阻塞 S1 互通。

**联调路径（第一期必须能用）：** Debug 设置页填服务器地址 + 「开发登录」调 `/auth/dev-login`。小程序同样 debug 开关。正式微信登录单独立项接到开放平台后再填 `WeChatClient` 真实现。

- [ ] **Step 1: 服务端加 `POST /auth/dev-login`，仅 `app.dev-login-enabled: true`（test/dev yml true，prod false）**

- [ ] **Step 2: Android「我的/抽屉」：未登录显示开发登录；已登录显示上传、邀请码输入、生成邀请（复制短码）**

- [ ] **Step 3: 打开账本 / 下拉：若有 cloudLedgerId 则 `pull`；写完 item 后 `pushItem`**（接 `ProjectRepository` 写成功回调）

- [ ] **Step 4: 401 清 jwt 并提示「请重新登录」；403 提示「没有这个账本的权限」；409 提示「该条已被其他人更新，请查看后再改」并已用服务器数据刷新**

- [ ] **Step 5: 仓库根目录 `sh oneClickSetup`**（block_until_ms ≥ 600000）

---

### Task 13: 小程序 API + 登录 + 同步

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-miniprogram/utils/api.js`
- Create: `utils/sync.js`
- Modify: `utils/store.js` — prefs.jwt、prefs.cloudUserId；project.cloudLedgerId、project.cloudRevision
- Modify: `pages/mine/mine.js` `mine.wxml` — 登录、上传、邀请
- Modify: `pages/list/list.js` `pages/overview/overview.js` — onShow pull；写后 push

`utils/api.js`：

```javascript
function request(path, { method = 'GET', data, token } = {}) {
  const base = wx.getStorageSync('serverBaseUrl') || 'http://127.0.0.1:8080'
  return new Promise((resolve, reject) => {
    wx.request({
      url: base + path,
      method,
      data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success(res) {
        if (res.statusCode === 401) reject({ code: 401, message: '请重新登录' })
        else if (res.statusCode === 403) reject({ code: 403, message: '没有这个账本的权限' })
        else if (res.statusCode === 409) reject({ code: 409, message: '该条已被其他人更新，请查看后再改', body: res.data })
        else if (res.statusCode === 410) reject({ code: 410, message: '邀请已失效' })
        else if (res.statusCode >= 400) reject({ code: res.statusCode, message: '请求失败' })
        else resolve(res.data)
      },
      fail: reject,
    })
  })
}
module.exports = { request }
```

开发登录：`request('/auth/dev-login', { method: 'POST', data: { label: 'mp' } })`（dev 开关打开时）。正式：`wx.login` → `/auth/wechat` `{ code, client: 'mp' }`。

`sync.pull` / `sync.pushItem` / `sync.importCurrent` 与 Android 语义一致；item 结构用现有 store 的 item + payments 数组。

`mine.wxml` 在 profile-card 下增加：未登录按钮「登录」；已登录「上传当前账本」「生成邀请码」「输入邀请码加入」。

- [ ] **Step 1: 实现 api.js + store 字段**

- [ ] **Step 2: mine 页登录/上传/邀请**

- [ ] **Step 3: overview/list onShow 若 cloudLedgerId 则 pull；store 写 item 后 push**

- [ ] **Step 4: 开发者工具勾选不校验合法域名，对 `127.0.0.1:8080` 走通 import + GET**

---

### Task 14: 全链路验收清单（手工）

不写新代码。在 Postgres + 服务 `./gradlew bootRun` 后：

1. Android 开发登录、上传「我家装修」、小程序开发登录、输入邀请码加入，两端都能看到同一项
2. 两端改 **不同项** 都能保存
3. 两端改 **同一项**（第二人未刷新）→ 409 文案，刷新后是第一人的数据
4. 所有者踢人后，对方 pull 403
5. 离线改一项，恢复网络后再打开会 push（pending 标记：store 里 `pendingItemIds`；Android 同）
6. Android：`sh oneClickSetup` 安装后再走一遍登录（若设备非模拟器，把 baseUrl 换成电脑局域网 IP）

---

## 实现时注意

- 金额始终 **Long 分**，JSON 不要用 Double。
- 标签随账本走：import 写入 `ledger_taxonomy`；GET 带回；本地切换云账本时覆盖当前 taxonomy prefs（Android `TaxonomyPrefs` 按 cloudLedgerId 分份或同步时整表替换当前账本缓存）。**最小做法：** 同步成功后用 snapshot.taxonomy 覆盖当前设备 TaxonomyPrefs（与「跟账本走」一致；切回未上传的本地账本时仍用设备 prefs——切云账本才覆盖）。
- 付款 `createdByUserId` 取 JWT；`createdByName` 取 User.nickname。
- 生产关闭 `dev-login-enabled`。

---

## Self-review（对照 spec）

| Spec | Task |
|------|------|
| 微信登录 JWT 30 天 | 2–3（真微信客户端可后接；dev-login 保证联调） |
| 手机号可选、占用 409、不合并双账号 | 10 |
| import / GET / PUT item / revision 409 | 5–6 |
| 邀请码 7 天、编辑者、踢人转让软删 30 天 | 8–9 |
| 打开/下拉同步、本地缓存 | 11–13 |
| 401/403/409/410 文案 | 12–13、error handler |
| Android + 小程序一期都接 | 11–13 |
| Google/iOS 登录不做、identity.provider 预留 | Task 2 枚举含 google/apple 但不实现 |
| 真微信开放平台 SDK | Task 12 明确后置，不阻塞互通 |

**缺口处理：** spec 要求正式微信登录。计划用 `WeChatClient` 接口 + 生产实现文件，S0 测试走 stub；真 appId 配好后只改 `WeChatClient` 实现。双端先 `dev-login` 打通同步，避免卡在开放平台审核。
