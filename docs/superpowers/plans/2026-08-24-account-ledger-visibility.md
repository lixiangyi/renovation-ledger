# 换账号账本可见性与绑定提示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录新账号后，按云端成员列表隐藏他人账本、未绑定本地本标「（本地）」并排末尾；登录时若当前本未绑定则弹窗询问上传（上传/取消均停留当前本）；他人本自动切到账号按上传时间的第一本。

**Architecture:** 纯函数 `LedgerVisibility`（Android）/ `ledgerVisibility.js`（小程序）根据本地 projects + `GET /ledgers` 摘要生成可见列表与展示名；服务端摘要增加 `createdAtEpochMs`；登录成功后 `refreshOnOpen` + 一次性绑定弹窗；403 静默切到第一本。

**Tech Stack:** Kotlin/Room/Compose、微信小程序、Spring Boot（`renovation-ledger-server`）

**Spec:** `docs/superpowers/specs/2026-08-24-account-ledger-visibility-design.md`

**Note:** 按仓库规则，本 plan 内所有 Commit 步骤**跳过**（勿执行 git）。

---

## File map

| 文件 | 职责 |
|------|------|
| `renovation-ledger-server/.../LedgerEntity.kt` | 增加 `createdAt` |
| `renovation-ledger-server/.../LedgerDtos.kt` + `LedgerService.kt` | summary 返回 `createdAtEpochMs`，list 按时间升序 |
| `app/.../domain/ledger/LedgerVisibility.kt` | 可见性/排序/展示名纯函数 |
| `app/.../domain/model/Project.kt` + Entity + Mappers + Migration 5→6 | `cloudLinkedAtEpochMs` |
| `app/.../data/remote/ApiModels.kt` | `LedgerSummaryDto.createdAtEpochMs` |
| `app/.../data/sync/LedgerSyncRepository.kt` | 写 linkedAt；403→切第一本；登录后 session 钩子 |
| `app/.../ui/overview/OverviewViewModel.kt` + Screen | 可见列表、绑定弹窗 |
| `app/.../ui/login/LoginViewModel.kt` + `WXEntryActivity` | 登录成功触发绑定检查 |
| `app/.../ui/mine/MineViewModel.kt` | 删除列表同规则 |
| `miniprogram/utils/ledgerVisibility.js` | 同规则 |
| `miniprogram/utils/sync.js` + `store.js` | linkedAt、403、登录后钩子 |
| `miniprogram/pages/login` + `overview` + `mine` | 弹窗与列表 |

---

### Task 1: 可见性纯函数（Android 单测先行）

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/domain/ledger/LedgerVisibility.kt`
- Create: `app/src/test/java/com/renovation/ledger/LedgerVisibilityTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.data.remote.LedgerSummaryDto
import com.renovation.ledger.domain.ledger.LedgerVisibility
import com.renovation.ledger.domain.ledger.VisibleLedger
import com.renovation.ledger.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerVisibilityTest {
    private fun p(
        id: String,
        name: String,
        cloudId: String? = null,
        linkedAt: Long? = null,
    ) = Project(
        id = id,
        name = name,
        memberNames = listOf("我"),
        cloudLedgerId = cloudId,
        cloudLinkedAtEpochMs = linkedAt,
    )

    @Test
    fun loggedOut_showsAllWithoutLocalSuffix() {
        val local = listOf(p("a", "A", "cloud-a"), p("b", "B", null))
        val out = LedgerVisibility.visible(
            projects = local,
            cloudSummaries = emptyList(),
            loggedIn = false,
        )
        assertEquals(2, out.size)
        assertEquals("A", out[0].displayName)
        assertEquals("B", out[1].displayName)
    }

    @Test
    fun loggedIn_hidesForeignCloud_appendsUnboundWithSuffix_sortedByUploadTime() {
        val local = listOf(
            p("foreign", "他人", "cloud-foreign", linkedAt = 1L),
            p("mine2", "我的晚", "cloud-late", linkedAt = 200L),
            p("local", "本地本", null),
            p("mine1", "我的早", "cloud-early", linkedAt = 100L),
        )
        val summaries = listOf(
            LedgerSummaryDto("cloud-late", "我的晚", "OWNER", 0, createdAtEpochMs = 200L),
            LedgerSummaryDto("cloud-early", "我的早", "OWNER", 0, createdAtEpochMs = 100L),
        )
        val out = LedgerVisibility.visible(local, summaries, loggedIn = true)
        assertEquals(listOf("我的早", "我的晚", "本地本（本地）"), out.map { it.displayName })
        assertTrue(out.none { it.project.id == "foreign" })
    }

    @Test
    fun firstAccountLedger_prefersEarliestCreatedAt() {
        val summaries = listOf(
            LedgerSummaryDto("b", "B", "OWNER", 0, 200L),
            LedgerSummaryDto("a", "A", "OWNER", 0, 100L),
        )
        assertEquals("a", LedgerVisibility.firstAccountCloudId(summaries))
    }
}
```

- [ ] **Step 2: 跑测确认失败**

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.LedgerVisibilityTest
```

Expected: FAIL（类不存在或编译失败）

- [ ] **Step 3: 实现 `LedgerVisibility` + 临时扩展 DTO/Project 字段（若尚无则先在本 Task 加最小字段）**

先确保 `LedgerSummaryDto` 有 `createdAtEpochMs: Long? = null`，`Project` 有 `cloudLinkedAtEpochMs: Long? = null`（完整 migration 在 Task 3）。

```kotlin
package com.renovation.ledger.domain.ledger

import com.renovation.ledger.data.remote.LedgerSummaryDto
import com.renovation.ledger.domain.model.Project

data class VisibleLedger(
    val project: Project,
    val displayName: String,
    val isLocalUnbound: Boolean,
)

object LedgerVisibility {
    fun visible(
        projects: List<Project>,
        cloudSummaries: List<LedgerSummaryDto>,
        loggedIn: Boolean,
    ): List<VisibleLedger> {
        if (!loggedIn) {
            return projects.map {
                VisibleLedger(it, it.name, isLocalUnbound = false)
            }
        }
        val cloudIds = cloudSummaries.map { it.id }.toSet()
        val createdAt = cloudSummaries.associate { it.id to it.createdAtEpochMs }
        val account = projects.filter {
            val cid = it.cloudLedgerId
            !cid.isNullOrBlank() && cid in cloudIds
        }.sortedBy { p ->
            val cid = p.cloudLedgerId!!
            createdAt[cid] ?: p.cloudLinkedAtEpochMs ?: Long.MAX_VALUE
        }
        val unbound = projects.filter { it.cloudLedgerId.isNullOrBlank() }
        return account.map {
            VisibleLedger(it, it.name, isLocalUnbound = false)
        } + unbound.map {
            VisibleLedger(it, it.name + "（本地）", isLocalUnbound = true)
        }
    }

    fun firstAccountCloudId(summaries: List<LedgerSummaryDto>): String? =
        summaries.minByOrNull { it.createdAtEpochMs ?: Long.MAX_VALUE }?.id

    fun isAccessible(project: Project, cloudIds: Set<String>, loggedIn: Boolean): Boolean {
        if (!loggedIn) return true
        val cid = project.cloudLedgerId
        if (cid.isNullOrBlank()) return true
        return cid in cloudIds
    }
}
```

- [ ] **Step 4: 再跑测**

Expected: PASS

- [ ] **Step 5: Commit** — 跳过

---

### Task 2: 服务端 `createdAtEpochMs`

**Files:**
- Modify: `/Users/beike/Projects/renovation-ledger-server/src/main/kotlin/com/renovation/ledger/server/ledger/LedgerEntity.kt`
- Modify: `.../LedgerDtos.kt`（`LedgerSummaryDto`）
- Modify: `.../LedgerService.kt`（`listLedgers` + create/import 写入）
- Test: `.../LedgerCreateTest.kt` 或新建断言 list 含时间并升序

- [ ] **Step 1: Entity 增加字段**

```kotlin
class LedgerEntity(
    @Id val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var revision: Long = 0,
    var ownerUserId: String = "",
    var createdAt: Instant = Instant.now(),
    var deletedAt: Instant? = null,
)
```

（若用 Flyway/liquibase，补 `ALTER TABLE ledgers ADD COLUMN created_at ...`；若 `ddl-auto=update`，确认本地库可加列。已有行默认 `now()` 可接受。）

- [ ] **Step 2: DTO**

```kotlin
data class LedgerSummaryDto(
    val id: String,
    val name: String,
    val role: String,
    val revision: Long,
    val createdAtEpochMs: Long = 0,
)
```

- [ ] **Step 3: listLedgers 填充并按 createdAt 升序**

```kotlin
fun listLedgers(): List<LedgerSummaryDto> {
    val userId = currentUserId()
    return members.findAllByUserId(userId).mapNotNull { member ->
        val ledger = ledgers.findById(member.ledgerId).orElse(null) ?: return@mapNotNull null
        if (ledger.deletedAt != null) return@mapNotNull null
        LedgerSummaryDto(
            id = ledger.id,
            name = ledger.name,
            role = member.role,
            revision = ledger.revision,
            createdAtEpochMs = ledger.createdAt.toEpochMilli(),
        )
    }.sortedBy { it.createdAtEpochMs }
}
```

创建/import 新建 `LedgerEntity` 时显式 `createdAt = Instant.now()`。

- [ ] **Step 4: 跑服务端相关测试**

```bash
cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests '*LedgerCreate*' --tests '*LedgerImport*'
```

Expected: PASS

- [ ] **Step 5: Commit** — 跳过

---

### Task 3: Android Room `cloudLinkedAtEpochMs` + DTO

**Files:**
- Modify: `Project.kt`, `ProjectEntity.kt`, `Mappers.kt`, `AppDatabase.kt`（version 6 + `MIGRATION_5_6`）, `AppModule.kt`
- Modify: `ApiModels.kt` `LedgerSummaryDto`

- [ ] **Step 1: Domain / Entity**

`Project` 增加 `cloudLinkedAtEpochMs: Long? = null`  
`ProjectEntity` 同名字段  
Mapper 双向映射

- [ ] **Step 2: Migration**

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN cloudLinkedAtEpochMs INTEGER")
    }
}
```

`@Database(version = 6)`，`AppModule` 注册 migration。

- [ ] **Step 3: ApiModels**

```kotlin
data class LedgerSummaryDto(
    val id: String,
    val name: String,
    val role: String,
    val revision: Long = 0,
    val createdAtEpochMs: Long? = null,
)
```

- [ ] **Step 4: 编译**

```bash
cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:compileDebugKotlin
```

Expected: SUCCESS

- [ ] **Step 5: Commit** — 跳过

---

### Task 4: `LedgerSyncRepository` — linkedAt、云列表缓存、403、选第一本

**Files:**
- Modify: `app/.../data/sync/LedgerSyncRepository.kt`
- Modify: `app/.../data/prefs/UserPrefs.kt`（可选：缓存最近一次 cloud summary ids；或用内存单例字段）

推荐：在 `LedgerSyncRepository` 内 `@Volatile` / 内存字段 `lastCloudSummaries: List<LedgerSummaryDto>`，`refreshOnOpen` 更新；UI 从 repo 读。

- [ ] **Step 1: `applySnapshot` 首次绑定时写 linkedAt**

```kotlin
val linkedAt = existing.cloudLinkedAtEpochMs
    ?: snapshot. /* 无则 */ System.currentTimeMillis()
// 若已有 cloudLedgerId 且相同，保留 existing.cloudLinkedAtEpochMs
projectDao.upsert(
    existing.copy(
        name = snapshot.name,
        cloudLedgerId = snapshot.id,
        cloudRevision = snapshot.revision,
        pendingSync = false,
        cloudLinkedAtEpochMs = existing.cloudLinkedAtEpochMs
            ?: System.currentTimeMillis(),
    ),
)
```

补占位 `ProjectEntity` 时：`cloudLinkedAtEpochMs = summary.createdAtEpochMs`。

- [ ] **Step 2: `refreshOnOpen` 返回/暴露 summaries；当前本不可访问则切第一本**

伪代码：

```kotlin
suspend fun refreshOnOpen(): LoginLedgerAction {
    if (jwt == null) return LoginLedgerAction.None
    fetchMe()
    val summaries = api.listLedgers(...)
    lastCloudSummaries = summaries
    // 补占位…
    val current = snapshotCurrent...
    val cloudIds = summaries.map { it.id }.toSet()
    return when {
        current.cloudLedgerId.isNullOrBlank() -> LoginLedgerAction.OfferBind(current.id, current.name)
        current.cloudLedgerId !in cloudIds -> {
            switchToFirstAccountLedger(summaries)
            LoginLedgerAction.SwitchedAway
        }
        else -> {
            pullCurrent()
            LoginLedgerAction.None
        }
    }
}
```

```kotlin
sealed class LoginLedgerAction {
    data object None : LoginLedgerAction()
    data class OfferBind(val projectId: String, val projectName: String) : LoginLedgerAction()
    data object SwitchedAway : LoginLedgerAction()
}
```

`switchToFirstAccountLedger`：`firstAccountCloudId` → 找本地 `cloudLedgerId` 匹配的 project → `setCurrentProjectId` → `pullCurrent`；无则建空本。

- [ ] **Step 3: `pullCurrent` 403**

捕获后调用 `switchToFirstAccountLedger`，`toast("已切换到当前账号的账本")`，**不要**再抛导致 Overview Toast「没有权限」。

- [ ] **Step 4: Commit** — 跳过

---

### Task 5: Android 登录弹窗 + 抽屉可见列表

**Files:**
- Modify: `LoginViewModel.kt` — `smsLogin` 成功后 `refreshOnOpen`，把 `OfferBind` 写入 prefs/SharedFlow（或 `UserPrefs` 一次性 pending）
- Modify: `WXEntryActivity.kt` — 同
- Modify: `OverviewViewModel.kt` / `OverviewScreen.kt` — 消费 pending 弹窗；`uiState.projects` 改为 `visibleLedgers`
- Modify: `MineViewModel.kt` — 删除列表用同一可见列表

- [ ] **Step 1: pending bind 状态**

简单做法：`UserPrefs` 增加 `pendingBindPrompt: Flow<Pair<id,name>?>` + `setPendingBindPrompt` / `clearPendingBindPrompt`。登录成功 `OfferBind` 时写入；Overview `LaunchedEffect` 弹出 `AlertDialog`。

确认 → `ledgerSync.importCurrent()` → clear  
取消 → clear，不切账本

- [ ] **Step 2: Overview 列表**

```kotlin
val visible = LedgerVisibility.visible(
    projects = projects,
    cloudSummaries = ledgerSync.lastCloudSummaries,
    loggedIn = jwt != null,
)
// uiState.projects 改为 List<VisibleLedger> 或平行 displayNames
```

抽屉 `Text(visible.displayName)`；`onSelect` 用 `project.id`。

`switchProject`：若目标无 `cloudLedgerId`，跳过 `pullCurrent`。

- [ ] **Step 3: 手动验证路径（设备）**

按 spec §10 场景 1–4。

- [ ] **Step 4: Commit** — 跳过

---

### Task 6: 小程序可见性 + store linkedAt

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-miniprogram/utils/ledgerVisibility.js`
- Modify: `utils/store.js` — project 默认 `cloudLinkedAtEpochMs: 0`；`addCloudPlaceholder` 写 `summary.createdAtEpochMs`
- Create: 可选简单 node 测试或手工对照 Android 用例

- [ ] **Step 1: `ledgerVisibility.js`**

镜像 Task 1 逻辑：`visible(projects, summaries, loggedIn)`、`firstAccountCloudId(summaries)`。

- [ ] **Step 2: overview / mine 列表**

```js
const vis = require('../../utils/ledgerVisibility')
const jwt = !!(state.prefs || {}).jwt
const summaries = require('../../utils/sync').getLastCloudSummaries() || []
const ledgers = vis.visible(state.projects || [], summaries, jwt)
// wxml 用 item.displayName
```

- [ ] **Step 3: Commit** — 跳过

---

### Task 7: 小程序 sync + 登录弹窗

**Files:**
- Modify: `utils/sync.js` — `refreshOnOpen` 返回 action；403 切第一本；`applySnapshot` 写 linkedAt
- Modify: `pages/login/login.js` — 登录成功后 `refreshOnOpen`，若 `offerBind` 则 `wx.showModal`
- Modify: `pages/overview/overview.js` — `switchLedger` 无 cloudId 不 pull；`onShow` 403 已在 sync 处理

- [ ] **Step 1: 弹窗文案与 Android 一致**

```js
wx.showModal({
  title: '绑定账本',
  content: '「' + name + '」尚未绑定账号。上传后将同步到当前账号；取消则仅本机使用。',
  confirmText: '上传',
  cancelText: '暂不上传',
  success(res) {
    if (res.confirm) {
      sync.importCurrent().then(...).catch(...)
    }
  },
})
```

- [ ] **Step 2: 验收对照 spec §10（小程序）**

- [ ] **Step 3: Commit** — 跳过

---

### Task 8: 双端收尾安装

- [ ] **Step 1:** Android 仓库根目录 `sh oneClickSetup`（`block_until_ms` ≥ 600000）
- [ ] **Step 2:** 汇报 BUILD / adb 结果；小程序开发者工具手动预览登录场景

---

## Spec coverage（自检）

| Spec 要求 | Task |
|-----------|------|
| 隐藏他人云本 | 1, 5, 6 |
| （本地）后缀 + 排末尾 | 1, 5, 6 |
| 上传时间排序 / 第一本 | 1, 2, 4 |
| 登录未绑定弹窗，上传/取消均停留 | 4, 5, 7 |
| 他人本自动切第一本、无无权限 Toast | 4, 7 |
| 自己云本停留 | 4 |
| 未登录全显 | 1 |
| createdAtEpochMs API | 2 |
| cloudLinkedAt 兜底 | 3, 4, 6 |
| 双端 | 5–7 |

## Placeholder scan

无 TBD；Commit 步骤统一跳过。
