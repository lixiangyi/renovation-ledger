package com.renovation.ledger.domain.metrics

import kotlin.math.abs
import kotlin.math.roundToInt

data class ProjectedSpendPercentResult(
    val percent: Int?,
    val gap: Long,
    val label: String,
)

object ProjectedSpendPercent {
    fun compute(projectedTotal: Long, totalBudget: Long): ProjectedSpendPercentResult {
        val gap = projectedTotal - totalBudget
        if (totalBudget == 0L) {
            return ProjectedSpendPercentResult(
                percent = null,
                gap = gap,
                label = "—",
            )
        }
        val percent = ((gap.toDouble() / totalBudget.toDouble()) * 100.0).roundToInt()
        val label = when {
            percent > 0 -> "预计超支 $percent%"
            percent < 0 -> "预计节省 ${abs(percent)}%"
            else -> "持平"
        }
        return ProjectedSpendPercentResult(
            percent = percent,
            gap = gap,
            label = label,
        )
    }
}
