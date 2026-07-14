package com.renovation.ledger.domain.model

data class BudgetItem(
    val id: String,
    val projectId: String,
    val name: String,
    val stage: String,
    val category: String = "",
    val space: String = "",
    val budgetAmount: Long,          // 分（¥1.00 = 100）
    val contractAmount: Long? = null,
    val merchant: String = "",
    val recordedDate: String? = null, // YYYY-MM-DD
    val remark: String = "",
    val isNewAddition: Boolean = false,
    val payments: List<Payment> = emptyList(),
)

fun BudgetItem.effectiveCost(): Long = contractAmount ?: budgetAmount

fun BudgetItem.deriveStatus(): ItemStatus {
    if (payments.isEmpty()) return ItemStatus.TO_BUY
    val allPaid = payments.all { it.status == PaymentStatus.PAID }
    val paidSum = payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
    val target = effectiveCost()
    return if (allPaid && paidSum >= target) ItemStatus.SETTLED else ItemStatus.PAYING
}
