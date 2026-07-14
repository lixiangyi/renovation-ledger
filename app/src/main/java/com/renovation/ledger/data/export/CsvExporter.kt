package com.renovation.ledger.data.export

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.model.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CsvExporter @Inject constructor() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun export(items: List<BudgetItem>): String {
        val sb = StringBuilder()
        sb.appendLine(CSV_HEADER)
        items.forEach { item ->
            val statusLabel = itemStatusLabel(item.deriveStatus())
            if (item.payments.isEmpty()) {
                sb.appendLine(rowForItem(item, payment = null, statusLabel = statusLabel))
            } else {
                item.payments.forEach { payment ->
                    sb.appendLine(rowForItem(item, payment, statusLabel))
                }
            }
        }
        return sb.toString()
    }

    private fun rowForItem(
        item: BudgetItem,
        payment: Payment?,
        statusLabel: String,
    ): String = listOf(
        escape(item.name),
        escape(item.stage),
        escape(item.category.ifBlank { "未分类" }),
        fenToYuan(item.budgetAmount),
        item.contractAmount?.let { fenToYuan(it) }.orEmpty(),
        escape(statusLabel),
        payment?.let { escape(paymentTypeLabel(it.type)) }.orEmpty(),
        payment?.let { fenToYuan(it.amount) }.orEmpty(),
        payment?.let { escape(paymentStatusLabel(it.status)) }.orEmpty(),
        payment?.paidAtEpochMs?.let { dateFormat.format(Date(it)) }.orEmpty(),
        payment?.createdBy?.let { escape(it) }.orEmpty(),
    ).joinToString(",")

    private fun escape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun fenToYuan(fen: Long): String = (fen / 100.0).let { v ->
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)
    }

    private fun itemStatusLabel(status: com.renovation.ledger.domain.model.ItemStatus): String =
        when (status) {
            com.renovation.ledger.domain.model.ItemStatus.TO_BUY -> "待购买"
            com.renovation.ledger.domain.model.ItemStatus.PAYING -> "付款中"
            com.renovation.ledger.domain.model.ItemStatus.SETTLED -> "已结清"
        }

    private fun paymentTypeLabel(type: PaymentType): String = type.label()

    private fun paymentStatusLabel(status: PaymentStatus): String = when (status) {
        PaymentStatus.PAID -> "已付"
        PaymentStatus.UNPAID -> "未付"
    }

    companion object {
        private const val CSV_HEADER =
            "项名称,阶段,分类,预算元,合同元,状态,付款类型,付款金额元,付款状态,日期,记账人"
    }
}
