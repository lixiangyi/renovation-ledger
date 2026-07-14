# Ledger Trash + Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hard-delete ledgers into a sandbox CSV trash with restore/purge, plus a Settings page (version / nickname / avatar) while keeping health color on Mine — on Android and WeChat miniprogram.

**Architecture:** Approach C — before hard-deleting a project, export full autosave-v1 CSV under `filesDir/trash/{id}.csv` (MP: `USER_DATA_PATH/trash/`) and update `index.json`. Restore re-imports that CSV as an active project and removes trash files. Settings is a new screen; Mine gains trash entry + settings gear and loses inline nickname/avatar editing.

**Tech Stack:** Kotlin, Room CASCADE, Hilt, Compose, DataStore, `AutosaveCsvCodec`, `com.renovation.ledger.dsl`; MP: `utils/store.js` + `wx.getFileSystemManager`, new `pages/trash` + `pages/settings`.

**Spec:** `docs/superpowers/specs/2026-07-14-ledger-trash-and-settings-design.md`

**Constraint:** Do **not** run git commit/status/diff unless the user explicitly asks in that message. Skip all “Commit” steps below.

---

## File map

### Android

| File | Role |
|------|------|
| Create `app/src/main/java/com/renovation/ledger/data/trash/TrashEntry.kt` | Index DTO |
| Create `app/src/main/java/com/renovation/ledger/data/trash/TrashStore.kt` | Read/write `filesDir/trash/` index + CSV |
| Create `app/src/test/java/com/renovation/ledger/TrashStoreTest.kt` | Index + empty CSV write tests (Robolectric or JVM File temp) |
| Modify `app/src/main/java/com/renovation/ledger/data/local/dao/ProjectDao.kt` | Add `@Delete` / `deleteById` |
| Modify `app/src/main/java/com/renovation/ledger/data/repo/ProjectRepository.kt` | `snapshotProject`, `moveProjectToTrash`, `restoreFromTrash`, `purgeTrashEntry` |
| Modify `app/src/main/java/com/renovation/ledger/ui/overview/OverviewScreen.kt` | Drawer delete control + confirm |
| Modify `app/src/main/java/com/renovation/ledger/ui/overview/OverviewViewModel.kt` | `deleteProject` |
| Create `app/src/main/java/com/renovation/ledger/ui/trash/TrashScreen.kt` | Trash list UI |
| Create `app/src/main/java/com/renovation/ledger/ui/trash/TrashViewModel.kt` | List / restore / purge |
| Create `app/src/main/java/com/renovation/ledger/ui/settings/SettingsScreen.kt` | Version, nickname, avatar |
| Create `app/src/main/java/com/renovation/ledger/ui/settings/SettingsViewModel.kt` | Profile + version |
| Modify `app/src/main/java/com/renovation/ledger/ui/mine/MineScreen.kt` | Gear, trash row, ledger delete, remove inline nickname/avatar edit |
| Modify `app/src/main/java/com/renovation/ledger/ui/mine/MineViewModel.kt` | projects list + delete; drop avatar/nickname save from Mine if unused |
| Modify `app/src/main/java/com/renovation/ledger/ui/navigation/AppNav.kt` | Routes `trash`, `settings` |

### Miniprogram

| File | Role |
|------|------|
| Create `utils/trashCsv.js` | Encode/decode autosave-v1 compatible CSV |
| Create `utils/trashStore.js` | `USER_DATA_PATH/trash/` index + CSV FS |
| Modify `utils/store.js` | `moveProjectToTrash`, `restoreFromTrash`, `purgeTrashEntry`, export trash CSV for any project |
| Modify `pages/overview/overview.js|wxml` | Drawer delete + confirm |
| Create `pages/trash/*` | Trash list page |
| Create `pages/settings/*` | Settings page (version, nickname, avatar) |
| Modify `pages/mine/*` | Gear, trash entry, ledger delete; remove inline nickname; keep health |
| Modify `app.json` | Register trash + settings pages |

---

### Task 1: Android TrashStore + ProjectDao.delete

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/trash/TrashEntry.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/trash/TrashStore.kt`
- Create: `app/src/test/java/com/renovation/ledger/TrashStoreTest.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/local/dao/ProjectDao.kt`

- [ ] **Step 1: Write failing TrashStore tests**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.data.trash.TrashEntry
import com.renovation.ledger.data.trash.TrashStore
import com.renovation.ledger.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var store: TrashStore
    private val codec = AutosaveCsvCodec()

    @Before
    fun setUp() {
        store = TrashStore(tmp.root, codec)
    }

    @Test
    fun writeAndList_emptyProjectCsv_andIndex() {
        val project = Project("p1", "测试账本", listOf("我"))
        val csv = codec.encode(AutosaveSnapshot(project, emptyList(), emptyList()))
        store.writeTrash(projectId = "p1", name = "测试账本", itemCount = 0, csvText = csv, deletedAt = 100L)
        val list = store.listEntries()
        assertEquals(1, list.size)
        assertEquals("p1", list[0].id)
        assertEquals("测试账本", list[0].name)
        assertEquals(0, list[0].itemCount)
        assertTrue(File(tmp.root, "trash/p1.csv").exists())
    }

    @Test
    fun list_sortedByDeletedAtDesc() {
        store.writeTrash("a", "A", 0, codec.encode(AutosaveSnapshot(Project("a","A",listOf("我")), emptyList(), emptyList())), 100L)
        store.writeTrash("b", "B", 0, codec.encode(AutosaveSnapshot(Project("b","B",listOf("我")), emptyList(), emptyList())), 200L)
        assertEquals(listOf("b", "a"), store.listEntries().map { it.id })
    }

    @Test
    fun removeEntry_deletesCsvAndIndex() {
        store.writeTrash("p1", "X", 0, codec.encode(AutosaveSnapshot(Project("p1","X",listOf("我")), emptyList(), emptyList())), 1L)
        store.removeEntry("p1")
        assertTrue(store.listEntries().isEmpty())
        assertTrue(!File(tmp.root, "trash/p1.csv").exists())
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class missing)**

Run: `cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.TrashStoreTest`

- [ ] **Step 3: Implement TrashEntry + TrashStore**

```kotlin
// TrashEntry.kt
package com.renovation.ledger.data.trash

class TrashEntry {
    var id: String = ""
    var name: String = ""
    var deletedAt: Long = 0L
    var itemCount: Int = 0
    var csvPath: String = ""
}

// TrashStore.kt — constructor(filesDir: File, codec: AutosaveCsvCodec)
// dir = File(filesDir, "trash"); index = File(dir, "index.json")
// writeTrash: mkdirs, write {id}.csv, upsert index entry (Gson / dsl gson), sort desc by deletedAt
// listEntries(): read index or empty; filter entries whose csv still exists optional? Spec: show index only
// readCsvText(id): read trash/{id}.csv
// removeEntry(id): remove from index + delete csv
```

Use `com.renovation.ledger.dsl` Gson helpers where natural. Prefer `@Inject constructor(@ApplicationContext context: Context, codec: AutosaveCsvCodec)` production ctor that uses `context.filesDir`; test ctor takes `File` root.

- [ ] **Step 4: Add ProjectDao delete**

```kotlin
@Query("DELETE FROM projects WHERE id = :id")
suspend fun deleteById(id: String)
```

- [ ] **Step 5: Re-run TrashStoreTest — expect PASS**

- [ ] **Step 6: Commit** — SKIP (no git unless user asks)

---

### Task 2: ProjectRepository trash operations

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/repo/ProjectRepository.kt`
- Inject `TrashStore`

- [ ] **Step 1: Add snapshot for any project**

```kotlin
suspend fun snapshotProjectWithItems(projectId: String): Pair<Project, List<BudgetItem>> {
    val entity = projectDao.getById(projectId) ?: error("账本不存在")
    val project = entity.toDomain()
    val itemEntities = itemDao.observeByProject(project.id).first()
    if (itemEntities.isEmpty()) return project to emptyList()
    val paymentEntities = paymentDao.observeByItems(itemEntities.map { it.id }).first()
    val paymentsByItemId = paymentEntities.groupBy { it.budgetItemId }
    val items = itemEntities.map { e ->
        e.toDomain(payments = paymentsByItemId[e.id].orEmpty().map { it.toDomain() })
    }
    return project to items
}
```

- [ ] **Step 2: Implement `moveProjectToTrash(projectId: String): Result<Unit>`**

Order (spec §3.5 / §3.7):
1. Snapshot project + items + flat payments
2. `codec.encode` via TrashStore → write CSV + index (`deletedAt = System.currentTimeMillis()`, `itemCount = items.size`)
3. If write fails → return failure, **no** DB delete
4. `projectDao.deleteById(projectId)` (CASCADE)
5. Retarget current:
   - If remaining projects non-empty: `setCurrentProjectId` to first remaining (name ASC = `getAll().first()`)
   - Else: `createProject("新账本")`
6. Call `autosaveNow()` for new current if applicable
7. On DB delete failure after trash write: try `trashStore.removeEntry(id)` and return failure message 「已备份但删除失败，请重试」

- [ ] **Step 3: Implement `restoreFromTrash(entryId: String): Result<Unit>`**

1. Read CSV; `decode`; if null → fail, keep index
2. Prefer CSV `project.id`; if `projectDao.getById` already exists (extreme), allocate new UUID and remap item.projectId
3. Upsert project + items + payments in transaction
4. `trashStore.removeEntry(entryId)`
5. `setCurrentProjectId` to restored id
6. `autosaveNow()`

- [ ] **Step 4: Implement `purgeTrashEntry(entryId: String): Result<Unit>`**

Confirm is UI-only; repo just `trashStore.removeEntry`.

- [ ] **Step 5: Expose `fun observeTrashEntries(): Flow` or suspend `listTrash()`** — ViewModel can call `trashStore.listEntries()` on refresh.

- [ ] **Step 6: Commit** — SKIP

---

### Task 3: Delete UX (Drawer + Mine)

**Files:**
- Modify OverviewScreen / OverviewViewModel
- Modify MineScreen / MineViewModel

- [ ] **Step 1: OverviewViewModel**

```kotlin
fun deleteProject(projectId: String) {
    viewModelScope.launch {
        repo.moveProjectToTrash(projectId)
            .onSuccess { /* toast via UiEvent or snackbar state */ }
            .onFailure { /* show message */ }
    }
}
```

- [ ] **Step 2: LedgerDrawerContent** — add delete IconButton (DeleteOutline) next to rename; parent shows `AlertDialog`:

Title/body semantics:
- 将「{name}」移入垃圾箱
- 会先导出备份，之后可从垃圾箱恢复
- 永久删除前仍可找回

Confirm → `viewModel.deleteProject(id)` + close dialog.

- [ ] **Step 3: Mine — ledger section**

Observe `observeProjects()`; each row: name + delete; same confirm dialog. Also add navigation callbacks `onOpenTrash`, `onOpenSettings` (wired in Task 4/5 if routes not ready — can stub until Task 4).

- [ ] **Step 4: Commit** — SKIP

---

### Task 4: Trash list UI (Android)

**Files:**
- Create TrashScreen.kt, TrashViewModel.kt
- Modify AppNav.kt

- [ ] **Step 1: Routes**

```kotlin
data object Trash : Route("trash")
data object Settings : Route("settings")
```

Register composables with back pop; hide bottom bar for these (same pattern as TaxonomyManage).

- [ ] **Step 2: TrashViewModel**

```kotlin
data class TrashUiState(val entries: List<TrashEntry> = emptyList(), val message: String? = null)
fun refresh(); fun restore(id); fun purge(id)
```

- [ ] **Step 3: TrashScreen**

TopAppBar 「垃圾箱」; LazyColumn rows: name, formatted `deletedAt`, `itemCount` 项; buttons 恢复 / 永久删除. Empty: 「暂无已删除账本」. Permanent delete: second `AlertDialog` emphasizing irreversible.

- [ ] **Step 4: Mine — row 「垃圾箱」→ navigate Trash**

- [ ] **Step 5: Commit** — SKIP

---

### Task 5: Settings page + Mine refactor (Android)

**Files:**
- Create SettingsScreen.kt, SettingsViewModel.kt
- Modify MineScreen.kt

- [ ] **Step 1: SettingsViewModel** — collect `userPrefs.userProfile`; `BuildConfig.VERSION_NAME`; `setNickname`; `AvatarStorage` save/clear (reuse Mine logic).

- [ ] **Step 2: SettingsScreen** — version Text; nickname field; avatar image + 更换/清除. **No** health color / trash / taxonomy.

- [ ] **Step 3: MineScreen** — TopAppBar right Settings gear → navigate Settings. Remove inline nickname TextField + avatar picker/edit from profile card (show read-only display name optional, or just drop editing). **Keep** health color section and import/export/taxonomy.

- [ ] **Step 4: Wire AppNav** Mine callbacks for settings + trash.

- [ ] **Step 5: Commit** — SKIP

---

### Task 6: Miniprogram parity

**Files:** as in File map (MP section)

- [ ] **Step 1: `utils/trashCsv.js`** — port autosave-v1 MAGIC `#renovation_ledger_autosave_v1` encode/decode for `{project, items[]}` with nested payments flattened to payment rows (mirror Android column layout). Empty ledger = project row only.

- [ ] **Step 2: `utils/trashStore.js`**

```js
// ensureDir: USER_DATA_PATH/trash
// listEntries / writeTrash / readCsv / removeEntry
```

- [ ] **Step 3: `store.js`**

```js
function moveProjectToTrash(projectId) { /* export full project CSV via trashCsv; writeTrash; splice projects+items; if none left createProject('新账本'); else retarget current */ }
function restoreFromTrash(id) { /* decode; upsert project+items; removeEntry; switchProject */ }
function purgeTrashEntry(id) { trashStore.removeEntry(id) }
function listTrash() { return trashStore.listEntries() }
```

Export `moveProjectToTrash`, `restoreFromTrash`, `purgeTrashEntry`, `listTrash`.

- [ ] **Step 4: overview drawer delete** — `wx.showModal` then `store.moveProjectToTrash`.

- [ ] **Step 5: pages/trash** — list, restore, purge with confirm.

- [ ] **Step 6: pages/settings** — `getApp().globalData.version`; nickname via `setPrefs`; avatar via `wx.chooseMedia` / chooseAvatar → save under `USER_DATA_PATH/avatars/` + `prefs.avatarPath`.

- [ ] **Step 7: mine** — nav right → settings; trash button; remove nickname edit; keep health; optional ledger delete list.

- [ ] **Step 8: Register pages in `app.json`**

- [ ] **Step 9: Commit** — SKIP

---

### Task 7: Verification

- [ ] **Step 1: Android unit tests**

`./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.TrashStoreTest`

- [ ] **Step 2: oneClickSetup**

```bash
cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup
```

`block_until_ms` ≥ 600000. Report BUILD SUCCESSFUL / adb install result.

- [ ] **Step 3: Manual smoke (device / MP)**

1. Delete from drawer → appears in trash; CSV under trash/
2. Delete last ledger → auto 「新账本」
3. Restore → data + becomes current
4. Purge → gone
5. Settings: version, nickname, avatar; health still on Mine

---

## Spec coverage checklist

| Spec requirement | Task |
|------------------|------|
| Trash layout index + CSV | 1, 6 |
| Hard delete CASCADE / storage splice | 2, 6 |
| Delete confirm drawer + Mine | 3, 6 |
| Restore / purge | 2, 4, 6 |
| Last/current → switch or create 新账本 | 2, 6 |
| Settings version/nick/avatar | 5, 6 |
| Health stays on Mine | 5, 6 |
| Empty ledger deletable | 1 (empty CSV), 2 |
| Export fail → no delete | 2 |
| oneClickSetup | 7 |
| Dual-platform | 6 |
| No soft-delete field | all |

---

## Execution note

User already approved with「开始吧」— execute this plan inline (executing-plans), skipping commit steps and the execution-choice prompt. Prefer vertical slices Task 1→7 in order.
