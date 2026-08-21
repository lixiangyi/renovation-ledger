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
