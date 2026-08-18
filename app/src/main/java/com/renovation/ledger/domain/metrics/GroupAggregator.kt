package com.renovation.ledger.domain.metrics

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.effectiveCost

data class GroupMetrics(
    val key: String,
    val budget: Long,
    val paid: Long,
    val projected: Long,
)

enum class GroupBy { STAGE, CATEGORY, SPACE }

enum class PieMetric { PAID, PROJECTED, BUDGET }

fun aggregate(items: List<BudgetItem>, groupBy: GroupBy): List<GroupMetrics> =
    items
        .groupBy { item ->
            when (groupBy) {
                GroupBy.STAGE -> item.stage.trim().ifEmpty { "未分阶段" }
                // 历史导入只写了 stage（旧账本「所属类别」），分类为空时回退到 stage
                GroupBy.CATEGORY -> item.category.trim().ifEmpty {
                    item.stage.trim().ifEmpty { "未分类" }
                }
                GroupBy.SPACE -> item.space.trim().ifEmpty { "未分空间" }
            }
        }
        .map { (key, groupItems) ->
            GroupMetrics(
                key = key,
                budget = groupItems.sumOf { it.budgetAmount },
                paid = groupItems.sumOf { item ->
                    item.payments
                        .filter { it.status == PaymentStatus.PAID }
                        .sumOf { it.amount }
                },
                projected = groupItems.sumOf { it.effectiveCost() },
            )
        }
        .sortedByDescending { it.projected }

/**
 * 饼图扇区交叉填充：按数值从大到小后，大块与小块交替排布，
 * 避免多个小扇区挤在同一角度导致外侧标注重叠。图例仍用原始顺序。
 */
fun <T> interleaveLargeAndSmall(items: List<T>, valueOf: (T) -> Long): List<T> {
    if (items.size <= 2) return items
    val sorted = items.sortedByDescending(valueOf)
    val result = ArrayList<T>(sorted.size)
    var lo = 0
    var hi = sorted.lastIndex
    var takeLarge = true
    while (lo <= hi) {
        if (takeLarge) {
            result.add(sorted[lo])
            lo += 1
        } else {
            result.add(sorted[hi])
            hi -= 1
        }
        takeLarge = !takeLarge
    }
    return result
}
