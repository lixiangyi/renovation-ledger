package com.renovation.ledger.domain.metrics

import com.renovation.ledger.domain.model.HealthLevel
import javax.inject.Inject

class HealthColorResolver @Inject constructor() {
    fun resolve(
        overspend: Long,
        totalBudget: Long,
        mildOverMaxPercent: Int = DEFAULT_MILD_OVER_MAX_PERCENT,
    ): HealthLevel {
        if (totalBudget <= 0L || overspend <= 0L) return HealthLevel.WITHIN
        val rate = overspend.toDouble() / totalBudget.toDouble()
        val mildMaxRate = percentToRate(mildOverMaxPercent)
        return if (rate <= mildMaxRate) HealthLevel.MILD_OVER else HealthLevel.SEVERE_OVER
    }

    companion object {
        const val MIN_MILD_OVER_MAX_PERCENT = 1
        const val MAX_MILD_OVER_MAX_PERCENT = 100
        /** 默认轻度超支上限 15%。 */
        const val DEFAULT_MILD_OVER_MAX_PERCENT = 15

        fun clampPercent(percent: Int): Int =
            percent.coerceIn(MIN_MILD_OVER_MAX_PERCENT, MAX_MILD_OVER_MAX_PERCENT)

        fun percentToRate(percent: Int): Double = clampPercent(percent) / 100.0
    }
}
