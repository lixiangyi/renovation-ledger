package com.renovation.ledger.domain.metrics

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import kotlin.math.roundToInt

data class PaidBudgetGap(
    val itemId: String,
    val itemName: String,
    val budgetAmount: Long,
    val paidAmount: Long,
    /** 正数：超支额或节余额（两侧均取绝对值）。 */
    val gapAmount: Long,
    /** 相对预算：gapAmount / budget × 100，预算为 0 时为 null。 */
    val gapPercent: Int?,
)

/**
 * 已花费展开：超支 / 节余。
 *
 * - **超支**：已付 > 预算（不论是否还在付尾款）
 * - **节余**：已结清且已付 < 预算
 *
 * 付款中且已付未超预算（典型：定金已付、尾款未付）两者都不进，避免误当成节余。
 */
object PaidBudgetGapClassifier {
    fun classify(items: List<BudgetItem>): Pair<List<PaidBudgetGap>, List<PaidBudgetGap>> {
        val overspend = mutableListOf<PaidBudgetGap>()
        val surplus = mutableListOf<PaidBudgetGap>()
        items.forEach { item ->
            val paid = item.payments
                .filter { it.status == PaymentStatus.PAID }
                .sumOf { it.amount }
            if (paid <= 0L) return@forEach
            val budget = item.budgetAmount
            val gap = paid - budget
            val status = item.deriveStatus()
            when {
                gap > 0L -> overspend += gapRow(item, paid, budget, gap)
                gap < 0L && status == ItemStatus.SETTLED ->
                    surplus += gapRow(item, paid, budget, -gap)
            }
        }
        return overspend.sortedByDescending { it.gapAmount } to
            surplus.sortedByDescending { it.gapAmount }
    }

    private fun gapRow(
        item: BudgetItem,
        paid: Long,
        budget: Long,
        gapAbs: Long,
    ) = PaidBudgetGap(
        itemId = item.id,
        itemName = item.name,
        budgetAmount = budget,
        paidAmount = paid,
        gapAmount = gapAbs,
        gapPercent = if (budget > 0L) {
            ((gapAbs.toDouble() / budget.toDouble()) * 100.0).roundToInt()
        } else {
            null
        },
    )
}
