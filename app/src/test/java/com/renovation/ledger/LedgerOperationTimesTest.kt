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
