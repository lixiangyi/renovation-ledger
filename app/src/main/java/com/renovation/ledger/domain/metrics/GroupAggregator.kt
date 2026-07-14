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
