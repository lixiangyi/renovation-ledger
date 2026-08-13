package com.renovation.ledger.domain.list

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.model.effectiveCost

enum class PaymentListGroupBy { STAGE, CATEGORY, SPACE }

enum class PaymentListLayout { NESTED, FLAT }

data class PaymentListGroupMetrics(
    val key: String,
    val items: List<BudgetItem>,
    val paidSum: Long,
    val budgetSum: Long,
    val projectedSum: Long,
    val paidItemCount: Int,
    val pendingItemCount: Int,
    val pendingAmountSum: Long,
)

data class FilterTabStat(val count: Int, val amountSum: Long)

data class FilterTabStats(
    val all: FilterTabStat,
    val toBuy: FilterTabStat,
    val paying: FilterTabStat,
    val settled: FilterTabStat,
)

/**
 * 支付清单聚合：大类指标与筛选 Tab 统计。
 *
 * - **实际支付 (paidSum)**：Σ 已付付款
 * - **预算 (budgetSum)**：Σ 预算
 * - **预计要支付 (projectedSum)**：Σ effectiveCost
 * - **已支付**：有已付的项数 + paidSum
 * - **待支付**：待购买或仍有未付的项；金额 = 待购买用 effectiveCost，付款中用未付行合计
 */
object PaymentListAggregator {

    fun showNewBadge(item: BudgetItem): Boolean {
        if (item.isNewAddition) return true
        val paid = item.payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
        return item.budgetAmount == 0L && paid > 0L
    }

    fun group(items: List<BudgetItem>, groupBy: PaymentListGroupBy): List<PaymentListGroupMetrics> {
        return items.groupBy { item ->
            when (groupBy) {
                PaymentListGroupBy.STAGE -> item.stage.ifBlank { "未分类" }
                PaymentListGroupBy.CATEGORY ->
                    item.category.ifBlank { item.stage }.ifBlank { "未分类" }
                PaymentListGroupBy.SPACE ->
                    item.space.ifBlank { "未指定" }
            }
        }.map { (key, groupItems) -> metrics(key, groupItems) }
            .sortedBy { it.key }
    }

    fun tabStats(items: List<BudgetItem>): FilterTabStats {
        fun stat(pred: (BudgetItem) -> Boolean): FilterTabStat {
            val subset = items.filter(pred)
            return FilterTabStat(
                count = subset.size,
                amountSum = subset.sumOf { it.effectiveCost() },
            )
        }
        return FilterTabStats(
            all = FilterTabStat(items.size, items.sumOf { it.effectiveCost() }),
            toBuy = stat { it.deriveStatus() == ItemStatus.TO_BUY },
            paying = stat { it.deriveStatus() == ItemStatus.PAYING },
            settled = stat { it.deriveStatus() == ItemStatus.SETTLED },
        )
    }

    private fun metrics(key: String, items: List<BudgetItem>): PaymentListGroupMetrics {
        var paidSum = 0L
        var budgetSum = 0L
        var projectedSum = 0L
        var paidItemCount = 0
        var pendingItemCount = 0
        var pendingAmountSum = 0L
        items.forEach { item ->
            val paid = item.payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
            val unpaid = item.payments.filter { it.status == PaymentStatus.UNPAID }.sumOf { it.amount }
            paidSum += paid
            budgetSum += item.budgetAmount
            projectedSum += item.effectiveCost()
            if (paid > 0L) paidItemCount++
            val status = item.deriveStatus()
            val isPending = status == ItemStatus.TO_BUY || unpaid > 0L
            if (isPending) {
                pendingItemCount++
                pendingAmountSum += when (status) {
                    ItemStatus.TO_BUY -> item.effectiveCost()
                    else -> unpaid
                }
            }
        }
        return PaymentListGroupMetrics(
            key = key,
            items = items,
            paidSum = paidSum,
            budgetSum = budgetSum,
            projectedSum = projectedSum,
            paidItemCount = paidItemCount,
            pendingItemCount = pendingItemCount,
            pendingAmountSum = pendingAmountSum,
        )
    }
}
