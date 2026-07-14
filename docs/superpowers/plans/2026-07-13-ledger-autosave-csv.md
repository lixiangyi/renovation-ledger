# Ledger Autosave CSV Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After every ledger write, overwrite a single round-trip autosave CSV (private + Download); when the DB has zero budget items, prompt the user to restore from that CSV.

**Architecture:** Pure `AutosaveCsvCodec` encodes/decodes v1 CSV. `LedgerAutosave` owns dual-file atomic overwrite and read/probe. `ProjectRepository` calls save after successful Room writes and exposes `restoreFromAutosave()`. `AppShellViewModel` detects empty DB + valid backup and drives a confirm dialog; silent `.db` heal/`LocalDbBackup` is removed.

**Tech Stack:** Kotlin, Room, Hilt, Jetpack Compose, DataStore (unchanged), JUnit unit tests, `oneClickSetup` for device verify.

**Spec:** `docs/superpowers/specs/2026-07-13-ledger-autosave-csv-design.md`

---

## File map

| File | Role |
|------|------|
| Create `app/src/main/java/com/renovation/ledger/data/autosave/AutosaveModels.kt` | Snapshot DTO: project + items + payments |
| Create `app/src/main/java/com/renovation/ledger/data/autosave/AutosaveCsvCodec.kt` | v1 encode/decode |
| Create `app/src/test/java/com/renovation/ledger/AutosaveCsvCodecTest.kt` | Round-trip + empty/invalid tests |
| Create `app/src/main/java/com/renovation/ledger/data/autosave/LedgerAutosave.kt` | Dual write, empty-skip, load/probe |
| Modify `app/src/main/java/com/renovation/ledger/data/repo/ProjectRepository.kt` | Replace `LocalDbBackup` with `LedgerAutosave`; add restore |
| Modify `app/src/main/java/com/renovation/ledger/di/AppModule.kt` | Remove `restoreFileIfNeeded` |
| Modify `app/src/main/java/com/renovation/ledger/MainActivity.kt` | Drop silent heal/snapshot; detection moves to shell |
| Modify `app/src/main/java/com/renovation/ledger/ui/navigation/AppShellViewModel.kt` | Empty+backup → pending restore UI state |
| Modify `app/src/main/java/com/renovation/ledger/ui/navigation/AppNav.kt` | Confirm dialog |
| Delete `app/src/main/java/com/renovation/ledger/data/backup/LocalDbBackup.kt` | Retired |
| Modify `app/src/main/AndroidManifest.xml` | Only if Download write needs a declared permission on minSdk |

---

### Task 1: AutosaveCsvCodec (TDD)

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/autosave/AutosaveModels.kt`
- Create: `app/src/main/java/com/renovation/ledger/data/autosave/AutosaveCsvCodec.kt`
- Test: `app/src/test/java/com/renovation/ledger/AutosaveCsvCodecTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.renovation.ledger

import com.renovation.ledger.data.autosave.AutosaveCsvCodec
import com.renovation.ledger.data.autosave.AutosaveSnapshot
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutosaveCsvCodecTest {
    private val codec = AutosaveCsvCodec()

    @Test
    fun round_trip_preserves_settle_payment() {
        val project = Project("p1", "我家装修", listOf("一口吞", "汤圆"))
        val item = BudgetItem(
            id = "i1",
            projectId = "p1",
            name = "全屋定制",
            stage = "全屋定制",
            category = "全屋定制",
            space = "全屋",
            budgetAmount = 105500_00L,
            contractAmount = 105500_00L,
            merchant = "",
            recordedDate = "2026-03-03",
            remark = "含衣柜",
            isNewAddition = true,
        )
        val payment = Payment(
            id = "pay1",
            budgetItemId = "i1",
            type = PaymentType.OTHER,
            amount = 105500_00L,
            status = PaymentStatus.PAID,
            paidAtEpochMs = 1_700_000_000_000L,
            note = "结清补差",
            createdBy = "一口吞",
        )
        val snapshot = AutosaveSnapshot(project, listOf(item), listOf(payment))
        val csv = codec.encode(snapshot)
        assertTrue(csv.startsWith("\uFEFF") || csv.contains("#renovation_ledger_autosave_v1"))
        val parsed = codec.decode(csv)!!
        assertEquals(project, parsed.project)
        assertEquals(1, parsed.items.size)
        assertEquals(item.copy(payments = emptyList()), parsed.items.single().copy(payments = emptyList()))
        assertEquals(payment, parsed.payments.single())
    }

    @Test
    fun decode_rejects_non_autosave() {
        assertNull(codec.decode("记账日期,所属类别,建材名称,金额,备注\n2026-01-01,家具,桌,1,\n"))
    }

    @Test
    fun summarize_counts_items_and_payments() {
        val csv = codec.encode(
            AutosaveSnapshot(
                Project("p", "x", listOf("我")),
                listOf(
                    BudgetItem("i", "p", "a", "s", "c", "", 100, 100, isNewAddition = true),
                ),
                listOf(
                    Payment("p1", "i", PaymentType.OTHER, 100, PaymentStatus.PAID, note = "结清补差"),
                ),
            ),
        )
        val summary = codec.summarize(csv)!!
        assertEquals(1, summary.itemCount)
        assertEquals(1, summary.paymentCount)
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (classes missing)**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.AutosaveCsvCodecTest`

Expected: compile failure or FAIL (symbol not found)

- [ ] **Step 3: Implement models + codec**

`AutosaveModels.kt`:

```kotlin
package com.renovation.ledger.data.autosave

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.Project

data class AutosaveSnapshot(
    val project: Project,
    val items: List<BudgetItem>,
    val payments: List<Payment>,
)

data class AutosaveSummary(
    val itemCount: Int,
    val paymentCount: Int,
)
```

`AutosaveCsvCodec.kt` must:

- Constant `MAGIC = "#renovation_ledger_autosave_v1"`
- Header exactly as spec §3
- `encode`: BOM + magic line + header + `project` row + each `item` + each `payment` (items written without nested payments; payments as separate rows)
- `decode`: require magic; parse CSV with quoted fields (reuse logic patterned on `DcjzCsvImporter.parseCsvLine`); build `AutosaveSnapshot`; ignore unknown `record_type`
- `summarize`: decode then counts, or count rows without full domain build
- Member names joined/split with `|`
- Empty `contract_fen` → null contract
- Enums via `PaymentType.valueOf` / `PaymentStatus.valueOf` with safe fail → null decode

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.AutosaveCsvCodecTest`

Expected: `BUILD SUCCESSFUL`, all tests PASS

- [ ] **Step 5: Commit** (if git allowed)

```bash
git add app/src/main/java/com/renovation/ledger/data/autosave/AutosaveModels.kt \
  app/src/main/java/com/renovation/ledger/data/autosave/AutosaveCsvCodec.kt \
  app/src/test/java/com/renovation/ledger/AutosaveCsvCodecTest.kt
git commit -m "feat: add autosave CSV v1 codec with round-trip tests"
```

---

### Task 2: LedgerAutosave dual write

**Files:**
- Create: `app/src/main/java/com/renovation/ledger/data/autosave/LedgerAutosave.kt`

- [ ] **Step 1: Implement `LedgerAutosave`**

```kotlin
package com.renovation.ledger.data.autosave

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LedgerAutosave @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codec: AutosaveCsvCodec,
) {
    private val mutex = Mutex()

    suspend fun save(snapshot: AutosaveSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (snapshot.items.isEmpty()) {
                Log.i(TAG, "skip save: empty items (refuse to overwrite backup)")
                return@withLock
            }
            val csv = codec.encode(snapshot)
            writePrivateAtomic(csv)
            writeDownloadBestEffort(csv)
        }
    }

    suspend fun loadPreferred(): AutosaveSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readPrivate()?.let { codec.decode(it) }
                ?: readDownload()?.let { codec.decode(it) }
        }
    }

    suspend fun probeSummary(): AutosaveSummary? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val text = readPrivate() ?: readDownload() ?: return@withLock null
            codec.summarize(text)
        }
    }

    private fun privateFile(): File = File(context.filesDir, PRIVATE_NAME)

    private fun writePrivateAtomic(csv: String) {
        val target = privateFile()
        val tmp = File(context.filesDir, "$PRIVATE_NAME.tmp")
        tmp.writeText(csv, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun readPrivate(): String? {
        val f = privateFile()
        if (!f.exists() || f.length() == 0L) return null
        return f.readText(Charsets.UTF_8)
    }

    private fun writeDownloadBestEffort(csv: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                // Try update existing display name first, else insert
                val existing = resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(DOWNLOAD_NAME),
                    null,
                )
                val uri = existing?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY).buildUpon()
                            .appendPath(id.toString()).build()
                            // Prefer: ContentUris.withAppendedId(collection, id)
                    } else null
                }
                val outUri = uri ?: resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, DOWNLOAD_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    },
                ) ?: return
                resolver.openOutputStream(outUri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val target = File(dir, DOWNLOAD_NAME)
                val tmp = File(dir, "$DOWNLOAD_NAME.tmp")
                tmp.writeText(csv, Charsets.UTF_8)
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "download write failed", it) }
    }

    private fun readDownload(): String? = runCatching {
        // Best-effort: MediaStore query by DISPLAY_NAME or legacy File read
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(DOWNLOAD_NAME),
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val id = c.getLong(0)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }
        } else {
            @Suppress("DEPRECATION")
            val f = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DOWNLOAD_NAME,
            )
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        }
    }.getOrNull()

    companion object {
        private const val TAG = "LedgerAutosave"
        const val PRIVATE_NAME = "ledger_autosave.csv"
        const val DOWNLOAD_NAME = "装修记账_自动备份.csv"
    }
}
```

Fix MediaStore URI construction in implementation to use `ContentUris.withAppendedId` (plan snippet comments it). Provide `AutosaveCsvCodec` via `@Inject constructor()` (no Hilt module needed if concrete class with `@Inject`).

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 3: Commit** (if git allowed)

```bash
git add app/src/main/java/com/renovation/ledger/data/autosave/LedgerAutosave.kt
git commit -m "feat: dual-write ledger autosave CSV to files and Download"
```

---

### Task 3: Wire ProjectRepository + remove LocalDbBackup

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/repo/ProjectRepository.kt`
- Modify: `app/src/main/java/com/renovation/ledger/di/AppModule.kt`
- Modify: `app/src/main/java/com/renovation/ledger/MainActivity.kt`
- Delete: `app/src/main/java/com/renovation/ledger/data/backup/LocalDbBackup.kt`

- [ ] **Step 1: Replace backup dependency in repository**

In `ProjectRepository`:

- Constructor: `localDbBackup: LocalDbBackup` → `ledgerAutosave: LedgerAutosave`
- After every successful write, call:

```kotlin
private suspend fun autosaveNow() {
    val (project, items) = observeProjectWithItems().first()
    val payments = items.flatMap { it.payments }
    val itemsBare = items.map { it.copy(payments = emptyList()) }
    ledgerAutosave.save(AutosaveSnapshot(project, itemsBare, payments))
}
```

- Replace each `localDbBackup.snapshot(...)` with `autosaveNow()`
- Add:

```kotlin
suspend fun restoreFromAutosave(): Result<AutosaveSummary> = runCatching {
    val snapshot = ledgerAutosave.loadPreferred()
        ?: error("没有可用的自动备份")
    if (snapshot.items.isEmpty()) error("自动备份里没有预算项")
    db.withTransaction {
        // Clear payments then items then replace project + insert all
        // Use existing DAOs: delete each payment/item or raw execSQL DELETE
        paymentDao /* need clearAll or raw */ 
        ...
    }
    ledgerAutosave.save(snapshot)
    AutosaveSummary(snapshot.items.size, snapshot.payments.size)
}
```

Add DAO clears if missing:

```kotlin
// PaymentDao
@Query("DELETE FROM payments")
suspend fun deleteAll()

// BudgetItemDao  
@Query("DELETE FROM budget_items")
suspend fun deleteAll()
```

Restore transaction order: `paymentDao.deleteAll()` → `itemDao.deleteAll()` → `projectDao.upsert(snapshot.project.toEntity())` → `itemDao.upsertAll(...)` → each `paymentDao.upsert(...)`.

- [ ] **Step 2: Clean AppModule / MainActivity / delete LocalDbBackup**

`AppModule.db`: remove `LocalDbBackup.restoreFileIfNeeded(ctx)` — builder only.

`MainActivity`: remove `LocalDbBackup` inject, `healIfEmpty`, `snapshot("app_start")`; keep `ensureDefaultProject()` only.

Delete `LocalDbBackup.kt`. Grep for `LocalDbBackup` / `healIfEmpty` / `restoreFileIfNeeded` — zero refs.

- [ ] **Step 3: Compile + unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.AutosaveCsvCodecTest`

Expected: PASS

- [ ] **Step 4: Commit** (if git allowed)

```bash
git add -u app/src/main/java/com/renovation/ledger
git commit -m "feat: autosave CSV after ledger writes; remove db file heal"
```

---

### Task 4: Empty-DB confirm dialog

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/navigation/AppShellViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/navigation/AppNav.kt`

- [ ] **Step 1: Extend shell state**

```kotlin
data class AppShellUiState(
    val healthLevel: HealthLevel = HealthLevel.WITHIN,
    val healthColorEnabled: Boolean = true,
    val pendingAutosaveRestore: AutosaveSummary? = null,
    val restoreMessage: String? = null,
)
```

On init (`viewModelScope.launch`):

```kotlin
projectRepository.ensureDefaultProject() // if not only in Activity — prefer single place: keep Activity ensure, shell only probes
val (_, items) = projectRepository.observeProjectWithItems().first()
if (items.isEmpty()) {
    val summary = ledgerAutosave.probeSummary()
    if (summary != null && summary.itemCount > 0) {
        _pendingRestore.value = summary
    }
}
```

Prefer: keep `ensureDefaultProject` in `MainActivity`; `AppShellViewModel` probes after subscribe. Use `MutableStateFlow` for pending + combine into `uiState`.

Methods:

```kotlin
fun dismissAutosaveRestore() { pending = null } // session only
fun confirmAutosaveRestore() {
  viewModelScope.launch {
    projectRepository.restoreFromAutosave()
      .onSuccess { /* clear pending, set toast message */ }
      .onFailure { /* set error message, keep pending or clear */ }
  }
}
```

- [ ] **Step 2: Dialog in `RenovationAppScaffold`**

When `shellState.pendingAutosaveRestore != null`, show `AlertDialog`:

- Title: `发现账本数据为空`
- Text: `检测到自动备份（约 ${n} 项 / ${m} 笔付款）。是否恢复？恢复将写入当前账本。`
- Confirm: `恢复` → `viewModel.confirmAutosaveRestore()`
- Dismiss: `暂不` → `viewModel.dismissAutosaveRestore()`

Optional Snackbar/Toast for `restoreMessage`.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:assembleDebug`

Expected: SUCCESS

- [ ] **Step 4: Commit** (if git allowed)

```bash
git add app/src/main/java/com/renovation/ledger/ui/navigation/
git commit -m "feat: confirm dialog to restore ledger from autosave CSV"
```

---

### Task 5: Device verification (`oneClickSetup`)

**Files:** none (manual / adb)

- [ ] **Step 1: Install**

Run: `sh oneClickSetup`

Expected: `install and start app`

- [ ] **Step 2: Verify autosave file appears after use**

With app having data, trigger any settle/edit, then:

```bash
adb shell "run-as com.renovation.ledger ls -la files/ledger_autosave.csv"
adb shell "ls -la /sdcard/Download/装修记账_自动备份.csv"
```

Expected: both exist (Download may fail on some OEMs — private must exist)

- [ ] **Step 3: Verify confirm restore**

```bash
# force-stop, wipe budget tables via pushed empty db OR DELETE via careful test,
# keep files/ledger_autosave.csv, start app → dialog → 恢复 → counts match
```

Safer path: use existing host backup only if needed; prefer in-app: after autosave exists, clear items using a debug-only path **or** document manual: uninstall not allowed; use `run-as` SQL only if available.

Minimal acceptance:

1. Cold start with non-empty DB → no dialog  
2. After writes → private CSV contains `#renovation_ledger_autosave_v1` and `结清补差` if settled  
3. If private CSV present and items forced to 0 before start → dialog shows → confirm restores items/payments  

- [ ] **Step 4: Update product spec one-liner** (optional)

In `docs/superpowers/specs/2026-07-13-renovation-ledger-design.md` data/export section, add: 自动备份为 autosave v1 单文件双写，空库需确认恢复.

- [ ] **Step 5: Commit docs** (if git allowed)

```bash
git add docs/superpowers/specs/
git commit -m "docs: note autosave CSV durability behavior"
```

---

## Spec coverage check

| Spec requirement | Task |
|------------------|------|
| Dual overwrite CSV private + Download | Task 2 |
| Only one file each side | Task 2 |
| Skip overwrite when items empty | Task 2 `save` |
| v1 magic + fen + project/item/payment rows | Task 1 |
| Write after every ledger mutation | Task 3 |
| Confirm dialog restore | Task 4 |
| Remove silent db heal / LocalDbBackup | Task 3 |
| Manual share export unchanged | (no task — do not modify `CsvExporter` format) |
| Device verify | Task 5 |

## Placeholder / consistency check

- Types: `AutosaveSnapshot`, `AutosaveSummary`, `LedgerAutosave`, `AutosaveCsvCodec` used consistently across tasks  
- No TBD steps  
- Git commits marked “if git allowed” because this environment may block `git`/`gh` hooks  

---

**Plan complete and saved to `docs/superpowers/plans/2026-07-13-ledger-autosave-csv.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  

**2. Inline Execution** — implement tasks in this session with checkpoints  

**Which approach?**
