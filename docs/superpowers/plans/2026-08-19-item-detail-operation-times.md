# 记账详情操作时间 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 预算项详情展示并编辑业务日期（记账日 / 付款日 / 结清日），同时只读展示系统「标记已付 / 标记已结清」时间；Android、小程序、云端快照对等。

**Architecture:** 领域层集中 `LedgerOperationTimes`（写入规则 + 旧数据回填 + 格式化）。`paidAtEpochMs` 改为系统标记已付时间，业务日用 `paidOnDate`；结清用预算项上的 `settledOnDate` / `settledAtEpochMs`。UI 只读系统时间；云端 DTO 原样透传新字段。

**Tech Stack:** Kotlin + Room + Compose + JUnit4；Spring JPA（`renovation-ledger-server`）；微信小程序 JS/WXML；收尾 Android `sh oneClickSetup`（本会话偏好=要）。

**Git：** 禁止自动 commit；仅用户明确要求时再提交。

**Spec：** `docs/superpowers/specs/2026-08-19-item-detail-operation-times-design.md`

---

## File map

| 文件 | 职责 |
|------|------|
| `domain/model/BudgetItem.kt` `Payment.kt` | 新字段 |
| `domain/model/LedgerOperationTimes.kt` | 纯函数：付款/结清盖戳、回填、格式化 |
| `LedgerOperationTimesTest.kt` | 规则单测（先写） |
| Room entity / `Mappers` / `AppDatabase` / `AppModule` | 持久化 + v4→v5 |
| `ProjectRepository.settleItem`、详情/录入 ViewModel、语音 executor、导入 mapper | 所有写入走 helper |
| `ItemDetailScreen.kt` | 信息卡 + 付款行 + 编辑日期 |
| `ApiModels` `LedgerSnapshotMapper` | 客户端快照 |
| `CsvExporter` `AutosaveCsvCodec` `DcjzCsvImporter` | 导出导入 |
| `renovation-ledger-server` `BudgetItemRow` `PaymentRow` `LedgerDtos` `LedgerService` | 云端落库 |
| 小程序 `operationTimes.js` `store.js` `sync.js` `pages/detail/*` `pages/entry/entry.js` `dcjzCsv.js` | 对等 |

---

### Task 1: 领域模型 + `LedgerOperationTimes`（TDD）

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/domain/model/Payment.kt`
- Modify: `app/src/main/java/com/renovation/ledger/domain/model/BudgetItem.kt`
- Create: `app/src/main/java/com/renovation/ledger/domain/model/LedgerOperationTimes.kt`
- Test: `app/src/test/java/com/renovation/ledger/LedgerOperationTimesTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `LedgerOperationTimesTest.kt`（此时 helper 还不存在，编译失败即预期）：

```kotlin
package com.renovation.ledger

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.LedgerOperationTimes
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LedgerOperationTimesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val nowMs = ZonedDateTime.of(2026, 3, 16, 14, 32, 0, 0, shanghai)
        .toInstant()
        .toEpochMilli()
    private val today = "2026-03-16"

    private fun payment(
        status: PaymentStatus = PaymentStatus.UNPAID,
        paidOnDate: String? = null,
        paidAtEpochMs: Long? = null,
        amount: Long = 1000,
        type: PaymentType = PaymentType.FINAL,
    ) = Payment(
        id = "p1",
        budgetItemId = "i1",
        type = type,
        amount = amount,
        status = status,
        paidOnDate = paidOnDate,
        paidAtEpochMs = paidAtEpochMs,
    )

    private fun item(payments: List<Payment>, budget: Long = 1000) = BudgetItem(
        id = "i1",
        projectId = "proj",
        name = "灯",
        stage = "软装",
        budgetAmount = budget,
        payments = payments,
    )

    @Test
    fun unpaidToPaid_defaultsTodayAndStampsNow() {
        val result = LedgerOperationTimes.applyPaymentStatus(
            current = payment(),
            newStatus = PaymentStatus.PAID,
            nowMs = nowMs,
            today = today,
        )
        assertEquals(PaymentStatus.PAID, result.status)
        assertEquals(today, result.paidOnDate)
        assertEquals(nowMs, result.paidAtEpochMs)
    }

    @Test
    fun unpaidToPaid_usesOverrideDate() {
        val result = LedgerOperationTimes.applyPaymentStatus(
            current = payment(),
            newStatus = PaymentStatus.PAID,
            nowMs = nowMs,
            today = today,
            paidOnDateOverride = "2026-03-01",
        )
        assertEquals("2026-03-01", result.paidOnDate)
        assertEquals(nowMs, result.paidAtEpochMs)
    }

    @Test
    fun paidToUnpaid_clearsBoth() {
        val result = LedgerOperationTimes.applyPaymentStatus(
            current = payment(PaymentStatus.PAID, "2026-01-01", 1L),
            newStatus = PaymentStatus.UNPAID,
            nowMs = nowMs,
            today = today,
        )
        assertEquals(PaymentStatus.UNPAID, result.status)
        assertNull(result.paidOnDate)
        assertNull(result.paidAtEpochMs)
    }

    @Test
    fun paidKeepPaid_amountEdit_keepsSystemTime() {
        val result = LedgerOperationTimes.applyPaymentStatus(
            current = payment(PaymentStatus.PAID, "2026-01-01", 99L),
            newStatus = PaymentStatus.PAID,
            nowMs = nowMs,
            today = today,
        )
        assertEquals("2026-01-01", result.paidOnDate)
        assertEquals(99L, result.paidAtEpochMs)
    }

    @Test
    fun paidKeepPaid_emptyDate_fillsTodayOnSave() {
        val result = LedgerOperationTimes.applyPaymentStatus(
            current = payment(PaymentStatus.PAID, null, 99L),
            newStatus = PaymentStatus.PAID,
            nowMs = nowMs,
            today = today,
        )
        assertEquals(today, result.paidOnDate)
        assertEquals(99L, result.paidAtEpochMs)
    }

    @Test
    fun explicitSettle_stampsTodayAndPaysUnpaid() {
        val before = item(
            listOf(
                payment(PaymentStatus.PAID, "2026-03-01", 1L, amount = 400, type = PaymentType.DEPOSIT),
                payment(PaymentStatus.UNPAID, amount = 600),
            ),
        )
        val settled = LedgerOperationTimes.explicitSettle(before, nowMs, today, nickname = "我")
        assertEquals("2026-03-16", settled.settledOnDate)
        assertEquals(nowMs, settled.settledAtEpochMs)
        assertEquals(2, settled.payments.size)
        assertEquals(true, settled.payments.all { it.status == PaymentStatus.PAID })
        val finalPay = settled.payments.first { it.type == PaymentType.FINAL }
        assertEquals(today, finalPay.paidOnDate)
        assertEquals(nowMs, finalPay.paidAtEpochMs)
    }

    @Test
    fun autoSettle_stampsFromLastPaymentWhenEmpty() {
        val paying = item(listOf(payment(PaymentStatus.UNPAID, amount = 1000)))
        val paid = LedgerOperationTimes.applyPaymentStatus(
            current = paying.payments[0],
            newStatus = PaymentStatus.PAID,
            nowMs = nowMs,
            today = today,
            paidOnDateOverride = "2026-03-10",
        )
        val after = LedgerOperationTimes.syncSettleFields(
            item = paying.copy(payments = listOf(paid)),
            nowMs = nowMs,
            today = today,
            forceStamp = false,
        )
        assertEquals("2026-03-10", after.settledOnDate)
        assertEquals(nowMs, after.settledAtEpochMs)
    }

    @Test
    fun autoSettle_doesNotOverwriteExistingSettleDate() {
        val already = item(listOf(payment(PaymentStatus.PAID, "2026-03-10", nowMs, 1000))).copy(
            settledOnDate = "2026-02-01",
            settledAtEpochMs = 50L,
        )
        val after = LedgerOperationTimes.syncSettleFields(already, nowMs, today, forceStamp = false)
        assertEquals("2026-02-01", after.settledOnDate)
        assertEquals(50L, after.settledAtEpochMs)
    }

    @Test
    fun unsettle_clearsSettleFields() {
        val settled = item(listOf(payment(PaymentStatus.PAID, "2026-03-10", nowMs, 1000))).copy(
            settledOnDate = today,
            settledAtEpochMs = nowMs,
        )
        val unpaid = LedgerOperationTimes.applyPaymentStatus(
            current = settled.payments[0],
            newStatus = PaymentStatus.UNPAID,
            nowMs = nowMs,
            today = today,
        )
        val after = LedgerOperationTimes.syncSettleFields(
            settled.copy(payments = listOf(unpaid)),
            nowMs,
            today,
            forceStamp = false,
        )
        assertNull(after.settledOnDate)
        assertNull(after.settledAtEpochMs)
    }

    @Test
    fun backfill_splitsPaidAtIntoPaidOnDate_andSettleFromLastPaid() {
        val old = item(
            listOf(payment(PaymentStatus.PAID, paidOnDate = null, paidAtEpochMs = nowMs, amount = 1000)),
        )
        val filled = LedgerOperationTimes.backfill(old, shanghai)
        assertEquals("2026-03-16", filled.payments[0].paidOnDate)
        assertEquals(nowMs, filled.payments[0].paidAtEpochMs)
        assertEquals("2026-03-16", filled.settledOnDate)
        assertEquals(nowMs, filled.settledAtEpochMs)
    }

    @Test
    fun formatDateTimeToMinute_usesLocalZone() {
        assertEquals(
            "2026-03-16 14:32",
            LedgerOperationTimes.formatDateTimeToMinute(nowMs, shanghai),
        )
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.LedgerOperationTimesTest`

Expected: FAIL（Unresolved reference `LedgerOperationTimes` / `paidOnDate`）

- [ ] **Step 3: 最小实现**

`Payment.kt` 在 `status` 后增加：

```kotlin
val paidOnDate: String? = null,      // YYYY-MM-DD 业务付款日
val paidAtEpochMs: Long? = null,     // 系统标记已付时间
```

保持 `paidAtEpochMs` 原位置亦可，但参数顺序不要拆散现有调用：把 `paidOnDate` 插在 `paidAtEpochMs` **前面**，所有位置参数调用会错位。因此 **`paidOnDate` 必须放在 `paidAtEpochMs` 之后、`note` 之前**，并给默认 `null`：

```kotlin
data class Payment(
    val id: String,
    val budgetItemId: String,
    val type: PaymentType,
    val amount: Long,
    val status: PaymentStatus,
    val paidAtEpochMs: Long? = null,
    val paidOnDate: String? = null,
    val note: String = "",
    val receiptUri: String? = null,
    val createdBy: String = "",
)
```

`BudgetItem.kt` 在 `isNewAddition` 后、`payments` 前增加：

```kotlin
val settledOnDate: String? = null,
val settledAtEpochMs: Long? = null,
val payments: List<Payment> = emptyList(),
```

`LedgerOperationTimes.kt`：

```kotlin
package com.renovation.ledger.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object LedgerOperationTimes {
    private val dateTimeMinute: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().format(isoDate)

    fun localDateString(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().format(isoDate)

    fun formatDateTimeToMinute(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(dateTimeMinute)

    fun applyPaymentStatus(
        current: Payment,
        newStatus: PaymentStatus,
        nowMs: Long,
        today: String,
        paidOnDateOverride: String? = null,
    ): Payment {
        val override = paidOnDateOverride?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            newStatus == PaymentStatus.UNPAID -> current.copy(
                status = PaymentStatus.UNPAID,
                paidOnDate = null,
                paidAtEpochMs = null,
            )
            current.status == PaymentStatus.PAID -> current.copy(
                status = PaymentStatus.PAID,
                paidOnDate = override ?: current.paidOnDate ?: today,
            )
            else -> current.copy(
                status = PaymentStatus.PAID,
                paidOnDate = override ?: current.paidOnDate ?: today,
                paidAtEpochMs = nowMs,
            )
        }
    }

    fun newPaymentTimes(
        status: PaymentStatus,
        nowMs: Long,
        today: String,
        paidOnDateOverride: String? = null,
    ): Pair<String?, Long?> {
        if (status != PaymentStatus.PAID) return null to null
        val override = paidOnDateOverride?.trim()?.takeIf { it.isNotEmpty() }
        return (override ?: today) to nowMs
    }

    fun syncSettleFields(
        item: BudgetItem,
        nowMs: Long,
        today: String,
        forceStamp: Boolean,
    ): BudgetItem {
        return if (item.deriveStatus() != ItemStatus.SETTLED) {
            item.copy(settledOnDate = null, settledAtEpochMs = null)
        } else if (forceStamp) {
            item.copy(settledOnDate = today, settledAtEpochMs = nowMs)
        } else if (item.settledAtEpochMs == null) {
            val lastPaid = lastPaid(item.payments)
            item.copy(
                settledOnDate = lastPaid?.paidOnDate ?: today,
                settledAtEpochMs = nowMs,
            )
        } else {
            item
        }
    }

    fun explicitSettle(
        item: BudgetItem,
        nowMs: Long,
        today: String,
        nickname: String,
    ): BudgetItem {
        val paidExisting = item.payments.map { payment ->
            if (payment.status == PaymentStatus.UNPAID) {
                applyPaymentStatus(payment, PaymentStatus.PAID, nowMs, today)
            } else {
                payment
            }
        }
        val paidSum = paidExisting.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
        val gap = item.effectiveCost() - paidSum
        val withGap = if (gap > 0L) {
            val (date, at) = newPaymentTimes(PaymentStatus.PAID, nowMs, today)
            paidExisting + Payment(
                id = UUID.randomUUID().toString(),
                budgetItemId = item.id,
                type = PaymentType.OTHER,
                amount = gap,
                status = PaymentStatus.PAID,
                paidAtEpochMs = at,
                paidOnDate = date,
                note = "结清补差",
                createdBy = nickname,
            )
        } else {
            paidExisting
        }
        return syncSettleFields(
            item.copy(payments = withGap),
            nowMs,
            today,
            forceStamp = true,
        )
    }

    fun backfill(item: BudgetItem, zone: ZoneId = ZoneId.systemDefault()): BudgetItem {
        val payments = item.payments.map { payment ->
            if (payment.status == PaymentStatus.PAID &&
                payment.paidOnDate.isNullOrBlank() &&
                payment.paidAtEpochMs != null
            ) {
                payment.copy(paidOnDate = localDateString(payment.paidAtEpochMs, zone))
            } else {
                payment
            }
        }
        val filled = item.copy(payments = payments)
        if (filled.deriveStatus() != ItemStatus.SETTLED) return filled
        if (filled.settledOnDate != null || filled.settledAtEpochMs != null) return filled
        val last = lastPaid(payments) ?: return filled
        return filled.copy(
            settledOnDate = last.paidOnDate ?: last.paidAtEpochMs?.let { localDateString(it, zone) },
            settledAtEpochMs = last.paidAtEpochMs,
        )
    }

    private fun lastPaid(payments: List<Payment>): Payment? =
        payments.filter { it.status == PaymentStatus.PAID }
            .maxWithOrNull(
                compareBy<Payment> { it.paidAtEpochMs ?: 0L }
                    .thenBy { it.paidOnDate.orEmpty() },
            )
}
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.LedgerOperationTimesTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit** — 跳过

---

### Task 2: Room 持久化 + migration 4→5

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/local/entity/BudgetItemEntity.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/local/entity/PaymentEntity.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/local/mapper/Mappers.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/renovation/ledger/di/AppModule.kt`

- [ ] **Step 1: Entity 加列**

`BudgetItemEntity` 在 `isNewAddition` 后：

```kotlin
val settledOnDate: String? = null,
val settledAtEpochMs: Long? = null,
```

`PaymentEntity` 在 `paidAtEpochMs` 后：

```kotlin
val paidOnDate: String? = null,
```

- [ ] **Step 2: Mapper 互转**

`BudgetItemEntity.toDomain` / `BudgetItem.toEntity` 带上 `settledOnDate`、`settledAtEpochMs`。  
`toDomain` 在拼好 payments 后调用 `LedgerOperationTimes.backfill(...)`（只影响读出对象，不写库、不推云）。

`PaymentEntity.toDomain` / `Payment.toEntity` 带上 `paidOnDate`。

- [ ] **Step 3: Migration**

`AppDatabase.kt`：

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budget_items ADD COLUMN settledOnDate TEXT")
        db.execSQL("ALTER TABLE budget_items ADD COLUMN settledAtEpochMs INTEGER")
        db.execSQL("ALTER TABLE payments ADD COLUMN paidOnDate TEXT")
        db.execSQL(
            """
            UPDATE payments
            SET paidOnDate = date(paidAtEpochMs / 1000, 'unixepoch', 'localtime')
            WHERE paidOnDate IS NULL
              AND paidAtEpochMs IS NOT NULL
              AND status = 'PAID'
            """.trimIndent(),
        )
    }
}
```

`@Database(..., version = 5, ...)`

`AppModule` 的 `addMigrations` 追加 `MIGRATION_4_5`。

- [ ] **Step 4: 编译**

Run: `./gradlew :app:compileDebugKotlin`

Expected: SUCCESS（`Payment(...)` 位置参数不受影响）

- [ ] **Step 5: Commit** — 跳过

---

### Task 3: 写入路径改走 helper

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/repo/ProjectRepository.kt`（`settleItem`）
- Modify: `app/src/main/java/com/renovation/ledger/ui/detail/ItemDetailViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/entry/ManualEntryViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/ui/entry/ConfirmEntryViewModel.kt`
- Modify: `app/src/main/java/com/renovation/ledger/voice/tool/executors/AddLedgerEntryExecutor.kt`
- Modify: `app/src/main/java/com/renovation/ledger/domain/importing/ImportedLineDraft.kt`
- Modify: `app/src/main/java/com/renovation/ledger/domain/importing/ImportMappers.kt`
- Modify: `app/src/main/java/com/renovation/ledger/domain/importing/DcjzCsvImporter.kt`（付款 draft 填 `paidOnDate`；完整 CSV 列在 Task 5）

- [ ] **Step 1: `settleItem` 改为 helper + 写回预算项**

把现有「逐条改付款 + 补差」替换为：

```kotlin
suspend fun settleItem(item: BudgetItem) {
    val now = System.currentTimeMillis()
    val today = LedgerOperationTimes.today(now)
    val nickname = userPrefs.userProfile.first().nickname
    val settled = LedgerOperationTimes.explicitSettle(item, now, today, nickname)
    db.withTransaction {
        itemDao.upsert(settled.toEntity())
        settled.payments.forEach { paymentDao.upsert(it.toEntity()) }
    }
    autosaveNow()
    runCatching { ledgerSync.get().pushItem(item.id) }
        .onFailure { err ->
            if (err !is StaleSyncException) {
                runCatching { ledgerSync.get().markPending() }
            }
        }
}
```

- [ ] **Step 2: 详情 `updatePayment` / `updateItem`**

`updatePayment` 增加 `paidOnDate: String`，构造完整 item 后 `syncSettleFields` 再 `upsertItem`（不要只 upsert 付款，否则结清字段清不掉）：

```kotlin
fun updatePayment(
    paymentId: String,
    type: PaymentType,
    amountYuan: String,
    status: PaymentStatus,
    note: String,
    paidOnDate: String,
) {
    val item = uiState.value.item ?: return
    val payment = item.payments.find { it.id == paymentId } ?: return
    val amountFen = parseYuanToFen(amountYuan) ?: return
    val now = System.currentTimeMillis()
    val today = LedgerOperationTimes.today(now)
    val updatedPay = LedgerOperationTimes.applyPaymentStatus(
        current = payment.copy(type = type, amount = amountFen, note = note.trim()),
        newStatus = status,
        nowMs = now,
        today = today,
        paidOnDateOverride = paidOnDate,
    )
    val payments = item.payments.map { if (it.id == paymentId) updatedPay else it }
    viewModelScope.launch {
        projectRepository.upsertItem(
            LedgerOperationTimes.syncSettleFields(
                item.copy(payments = payments),
                now,
                today,
                forceStamp = false,
            ),
        )
    }
}
```

`updateItem` 增加 `settledOnDate: String` 参数：仅当 `item.deriveStatus() == ItemStatus.SETTLED` 时写入 `settledOnDate.trim().ifBlank { null }`，**不要**改 `settledAtEpochMs`。

- [ ] **Step 3: 新建已付付款**

`ManualEntryViewModel.addPayment`、`ConfirmEntryViewModel.buildPayment`、`AddLedgerEntryExecutor` 里创建 `Payment` 时：

```kotlin
val now = System.currentTimeMillis()
val today = LedgerOperationTimes.today(now)
val (paidOnDate, paidAt) = LedgerOperationTimes.newPaymentTimes(status, now, today)
Payment(..., status = status, paidAtEpochMs = paidAt, paidOnDate = paidOnDate, ...)
```

加完付款后若会自动结清：读出该项（或内存 copy），`syncSettleFields(..., forceStamp = false)` 再 `upsertItem`。`addPayment` 目前只 `upsertPayment`，改为：取出 item → 追加 payment → `syncSettleFields` → `upsertItem`。

- [ ] **Step 4: 导入**

`ImportedPaymentDraft` 增加 `paidOnDate: String? = null`。  
`DcjzCsvImporter` 解析到日期且已付时：`paidOnDate = date`，`paidAtEpochMs = parseDateEpoch(date)`（导入时刻不当系统操作时间）。  
`ImportMappers` 把 `paidOnDate` 抄到 `Payment`。导入完成后对每个 item `backfill` 一次再入库（已有日期则 backfill 不覆盖）。

- [ ] **Step 5: 跑已有单测**

Run: `./gradlew :app:testDebugUnitTest`

Expected: SUCCESS。若 `AddLedgerEntryExecutorTest` 因构造参数失败，只补 `paidOnDate`。

- [ ] **Step 6: Commit** — 跳过

---

### Task 4: Android 详情 UI

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/ui/detail/ItemDetailScreen.kt`

- [ ] **Step 1: 信息卡**

`InfoCard` 增加 `status` 已有。记账日期始终一行：`item.recordedDate ?: "—"`。  
`status == ItemStatus.SETTLED` 时：

```kotlin
InfoRow("结清日期", item.settledOnDate ?: "—")
item.settledAtEpochMs?.let { InfoRow("结清操作时间", LedgerOperationTimes.formatDateTimeToMinute(it)) }
```

未结清不渲染结清行。

- [ ] **Step 2: 付款行**

`PaymentRow` 在备注/记账人下方，仅 `payment.status == PaymentStatus.PAID`：

```kotlin
val paidDay = payment.paidOnDate ?: "—"
val marked = payment.paidAtEpochMs?.let { LedgerOperationTimes.formatDateTimeToMinute(it) }
Text(
    text = if (marked != null) "付款日 $paidDay · 标记已付 $marked" else "付款日 $paidDay",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

- [ ] **Step 3: 编辑预算项**

`EditItemDialog` 的 `onConfirm` 增加 `settledOnDate: String`。已有 `DatePickerField` 记账日期。`item.deriveStatus() == ItemStatus.SETTLED` 时再加：

```kotlin
DatePickerField(
    label = "结清日期",
    value = settledOnDate.ifBlank { null },
    onDateSelected = { settledOnDate = it.orEmpty() },
)
```

调用 `viewModel.updateItem(..., settledOnDate)`。

- [ ] **Step 4: 编辑付款**

`EditPaymentDialog` 的 `onConfirm` 增加 `paidOnDate: String`。`status == PaymentStatus.PAID` 时显示 `DatePickerField(label = "付款日", ...)`。系统时间不进表单。

`onConfirm(type, amount, status, note, paidOnDate)` → `viewModel.updatePayment(..., paidOnDate)`。

- [ ] **Step 5: Commit** — 跳过

---

### Task 5: 快照 DTO + CSV

**Files:**
- Modify: `app/src/main/java/com/renovation/ledger/data/remote/ApiModels.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/sync/LedgerSnapshotMapper.kt`
- Modify: `app/src/test/java/com/renovation/ledger/LedgerSnapshotMapperTest.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/export/CsvExporter.kt`
- Modify: `app/src/main/java/com/renovation/ledger/data/autosave/AutosaveCsvCodec.kt`
- Modify: `app/src/test/java/com/renovation/ledger/AutosaveCsvCodecTest.kt`
- Modify: `app/src/test/java/com/renovation/ledger/DcjzCsvImporterTest.kt`（新导出多列仍能解析）
- Modify: `app/src/main/java/com/renovation/ledger/domain/importing/DcjzCsvImporter.kt`（parseNative 填 `paidOnDate`）

- [ ] **Step 1: 快照测试先扩**

`LedgerSnapshotMapperTest.itemRoundTripPreservesFenAndPayments` 给 Payment 加上 `paidOnDate = "2026-08-01"`，给 BudgetItem 加上 `settledOnDate = "2026-08-02"`、`settledAtEpochMs = 2L`。断言 round-trip 后这些字段相等。

跑测：FAIL（DTO 无字段）。

- [ ] **Step 2: DTO + mapper**

`ApiPaymentDto` 增加 `val paidOnDate: String? = null`。  
`ApiItemDto` 增加 `val settledOnDate: String? = null`、`val settledAtEpochMs: Long? = null`。

`toDto` / `toDomain` 原样映射。缺字段 Gson 当 null。

跑 `LedgerSnapshotMapperTest`：PASS。

- [ ] **Step 3: 用户 CSV**

`CSV_HEADER` 改为：

`项名称,阶段,分类,预算元,合同元,状态,付款类型,付款金额元,付款状态,日期,记账人,标记已付时间,结清日期,结清操作时间`

`日期` 列 = `payment?.paidOnDate.orEmpty()`（不再从 `paidAtEpochMs` format）。  
`标记已付时间` = `paidAtEpochMs` 用 `LedgerOperationTimes.formatDateTimeToMinute`。  
结清两列从 item 重复写出。

`parseNative`：`日期` → `paidOnDate`（已付时）；`paidAtEpochMs` 仍 `parseDateEpoch(日期)`。若有「标记已付时间」列且能解析则覆盖 epoch（可用 `yyyy-MM-dd HH:mm` 的 `SimpleDateFormat`，失败则保留日期午夜）。

旧 11 列表头继续识别（`isNativeHeader` 已按「项名称」判断）。

- [ ] **Step 4: 自动备份 CSV**

`HEADER` 末尾追加 `,paid_on_date,settled_on_date,settled_at_epoch_ms`。

`encodeItemRow` 在 `is_new_addition` 后不要插入（会挤占 payment 列）。**只在整行末尾追加**：item 行写空、空、`settledOnDate`、`settledAtEpochMs`；更干净的做法：统一 25 列，下标：

- 22 `paid_on_date`（payment 行有值）
- 23 `settled_on_date`（item 行有值）
- 24 `settled_at_epoch_ms`（item 行有值）

`encodeItemRow` / `encodePaymentRow` 补这三列。  
`parseItemRow`：`settledOnDate = cols.getOrNull(23)`，`settledAtEpochMs = cols.getOrNull(24)?.toLongOrNull()`。  
`parsePaymentRow`：`paidOnDate = cols.getOrNull(22)`。旧文件无这些列则为 null。

`AutosaveCsvCodecTest.round_trip_preserves_settle_payment` 给 payment `paidOnDate = "2023-11-15"`、item `settledOnDate`/`settledAtEpochMs`，断言相等。

- [ ] **Step 5: 跑测**

Run: `./gradlew :app:testDebugUnitTest --tests com.renovation.ledger.LedgerSnapshotMapperTest --tests com.renovation.ledger.AutosaveCsvCodecTest --tests com.renovation.ledger.DcjzCsvImporterTest`

Expected: SUCCESS

- [ ] **Step 6: Commit** — 跳过

---

### Task 6: 云端落库

工作目录：`/Users/beike/Projects/renovation-ledger-server`（`ddl-auto: update`，加实体字段即可）。

**Files:**
- Modify: `src/main/kotlin/com/renovation/ledger/server/ledger/BudgetItemRow.kt`
- Modify: `src/main/kotlin/com/renovation/ledger/server/ledger/PaymentRow.kt`
- Modify: `src/main/kotlin/com/renovation/ledger/server/ledger/LedgerDtos.kt`
- Modify: `src/main/kotlin/com/renovation/ledger/server/ledger/LedgerService.kt`
- Modify: `src/test/kotlin/com/renovation/ledger/server/ledger/ItemSyncServiceTest.kt`

- [ ] **Step 1: 写失败测试**

在 `ItemSyncServiceTest` 追加：

```kotlin
@Test
fun putItemPersistsOperationTimes() {
    val token = login("sync_times")
    val ledger = import(token, "proj_sync_times")
    val updated = ledger.items[0].copy(
        settledOnDate = "2026-03-16",
        settledAtEpochMs = 1_773_640_320_000L,
        payments = listOf(
            PaymentDto(
                id = "pay_1",
                type = "FINAL",
                amount = 10000,
                status = "PAID",
                paidAtEpochMs = 1_773_640_320_000L,
                paidOnDate = "2026-03-15",
                createdByName = "我",
            ),
        ),
    )
    putItem(token, ledger.id, updated, ledger.revision, 200)
    val again = getLedger(token, ledger.id)
    assertEquals("2026-03-16", again.items[0].settledOnDate)
    assertEquals(1_773_640_320_000L, again.items[0].settledAtEpochMs)
    assertEquals("2026-03-15", again.items[0].payments[0].paidOnDate)
    assertEquals(1_773_640_320_000L, again.items[0].payments[0].paidAtEpochMs)
}
```

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test --tests com.renovation.ledger.server.ledger.ItemSyncServiceTest.putItemPersistsOperationTimes`

Expected: 编译失败或断言失败。

- [ ] **Step 2: 实体 + DTO + 读写**

`BudgetItemRow`：`var settledOnDate: String? = null`，`var settledAtEpochMs: Long? = null`。  
`PaymentRow`：`var paidOnDate: String? = null`。  
`ItemDto` / `PaymentDto` 同样默认 null。

`LedgerService.writeItem` 写入这些字段；`toDto` 读出。缺字段 JSON 保持默认 null，旧客户端不崩。

- [ ] **Step 3: 再跑测试**

Run: `./gradlew test --tests com.renovation.ledger.server.ledger.ItemSyncServiceTest`

Expected: SUCCESS

- [ ] **Step 4: Commit** — 跳过

---

### Task 7: 小程序对等

**Files:**
- Create: `/Users/beike/Projects/renovation-ledger-miniprogram/utils/operationTimes.js`
- Modify: `utils/sync.js` `utils/store.js` `utils/dcjzCsv.js` `utils/trashCsv.js`（若有付款列则追加，与 Android autosave 对齐）
- Modify: `pages/detail/detail.js` `detail.wxml`
- Modify: `pages/entry/entry.js`

- [ ] **Step 1: JS helper（与 Kotlin 规则一致）**

`operationTimes.js` 导出：`today(nowMs)`、`formatDateTimeToMinute(epochMs)`、`applyPaymentStatus`、`newPaymentTimes`、`syncSettleFields`、`explicitSettle`、`backfill`。  
日期用本地 `Date`：`YYYY-MM-DD`；到分用

```javascript
function pad(n) { return n < 10 ? '0' + n : String(n) }
function formatDateTimeToMinute(epochMs) {
  const d = new Date(epochMs)
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
    ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}
```

`explicitSettle` 补差逻辑抄现有 `pages/detail/detail.js` 的 `settle()`，时间字段走 helper。需要 `deriveStatus` / `effectiveCost` / `uid`：从 `model.js` require，避免循环则把 settle 补差留在 page 里、只把 `applyPaymentStatus` + `syncSettleFields` 放 helper。

推荐：page 的 `settle()` 改为：

```javascript
const times = require('../../utils/operationTimes')
const now = Date.now()
const today = times.today(now)
const nickname = store.getState().prefs.nickname
store.upsertItem(times.explicitSettle(item, now, today, nickname, {
  uid: uid,
  effectiveCost: effectiveCost,
  PaymentType: PaymentType,
  PaymentStatus: PaymentStatus,
}))
```

helper 内补差创建付款必须带 `paidOnDate` + `paidAtEpochMs`。

- [ ] **Step 2: 读缓存回填**

`store.getItem` / `viewOf` 对每个 item 调 `backfill`（内存展示）。**不要**因 backfill 调 `pushItem`。

- [ ] **Step 3: sync DTO**

`paymentToDto` / `paymentFromDto` 增加 `paidOnDate`。  
`itemToDto` / `itemFromDto` 增加 `settledOnDate`、`settledAtEpochMs`。

- [ ] **Step 4: 详情展示**

`detail.js` 的 `view`：

- `recordedDateText: item.recordedDate || '—'`
- 已结清：`settledOnDate`、`settledAtText: formatDateTimeToMinute(settledAtEpochMs)`
- 付款：`paidOnDate`、`markedPaidText`，`showTimes: status === '已付'`

`detail.wxml` 信息卡增加记账日期；`wx:if="{{view.settled}}"` 结清两行。付款 `wx:if="{{item.showTimes}}"` 显示 `付款日 · 标记已付`。

编辑预算项：`mode="date"` 的 picker 编辑 `recordedDate`；已结清再显示结清日 picker。保存写入 `settledOnDate`，不动 `settledAtEpochMs`。

编辑付款：已付时 date picker 绑定 `editPaidOnDate`，`savePay` 走 `applyPaymentStatus` + `syncSettleFields` 再 `upsertItem`。

- [ ] **Step 5: 录入**

`entry.js` 创建已付付款时用 `newPaymentTimes`。追加到已有项后 `syncSettleFields`。

导入 `dcjzCsv.js`：已付行 `paidOnDate = date`，`paidAtEpochMs = parseDateEpoch(date)`。

- [ ] **Step 6: Commit** — 跳过

---

### Task 8: 全量回归 + 手测清单

- [ ] **Step 1: Android 单测**

Run: `cd /Users/beike/Projects/renovation-ledger && ./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Server 单测**

Run: `cd /Users/beike/Projects/renovation-ledger-server && ./gradlew test`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 安装 Android**

Run: `cd /Users/beike/Projects/renovation-ledger && sh oneClickSetup`（`block_until_ms` ≥ 600000）

Expected: Gradle `BUILD SUCCESSFUL`，adb 安装成功。

- [ ] **Step 4: 手测**

1. 新建项，只填记账日期 → 详情有记账日期，无结清行  
2. 加未付尾款 → 付款行无时间  
3. 把尾款标已付 → 出现付款日（今天）和标记已付（到分）；若已结清，信息卡出现结清日=付款日、结清操作时间≈现在  
4. 编辑付款日为昨天 → 付款日变、标记已付不变  
5. 改回未付 → 结清行消失  
6. 点「标记为已结清」→ 结清日今天、未付变已付  
7. 编辑结清日可改可清空（状态仍已结清）  
8. 小程序同一条云同步项，字段一致  

- [ ] **Step 5: Commit** — 跳过

---

## Spec coverage

| Spec 节 | Task |
|---------|------|
| §3 模型 | 1, 2 |
| §4 写入规则 | 1, 3, 7 |
| §5 详情 UI | 4, 7 |
| §6 迁移/回填 | 1 backfill, 2 SQL, 7 store |
| §7.1 云端 | 5 DTO, 6 server |
| §7.2–7.4 CSV | 5 |
| §8 时区/空值 | 1 格式化, 4/7 不展示 |
| §9 测试 | 1, 5, 6, 8 |
| 结清日可清空不取消结清 | 3 `updateItem` |
| 回填不单独推云 | 2 mapper / 7 getItem |

无 TBD；`paidOnDate` 在 `paidAtEpochMs` 之后以免打爆位置参数。
