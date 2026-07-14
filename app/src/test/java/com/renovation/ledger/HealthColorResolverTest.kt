package com.renovation.ledger

import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.HealthLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthColorResolverTest {
    private val r = HealthColorResolver()

    @Test
    fun within_budget() {
        assertEquals(HealthLevel.WITHIN, r.resolve(overspend = -1, totalBudget = 100))
        assertEquals(HealthLevel.WITHIN, r.resolve(overspend = 0, totalBudget = 100))
    }

    @Test
    fun mild_and_severe_default_15() {
        assertEquals(
            HealthLevel.MILD_OVER,
            r.resolve(overspend = 10, totalBudget = 100),
        )
        assertEquals(
            HealthLevel.MILD_OVER,
            r.resolve(overspend = 15, totalBudget = 100),
        )
        assertEquals(
            HealthLevel.SEVERE_OVER,
            r.resolve(overspend = 16, totalBudget = 100),
        )
    }

    @Test
    fun mild_threshold_is_configurable() {
        assertEquals(
            HealthLevel.MILD_OVER,
            r.resolve(overspend = 50, totalBudget = 100, mildOverMaxPercent = 50),
        )
        assertEquals(
            HealthLevel.SEVERE_OVER,
            r.resolve(overspend = 51, totalBudget = 100, mildOverMaxPercent = 50),
        )
        assertEquals(
            HealthLevel.SEVERE_OVER,
            r.resolve(overspend = 2, totalBudget = 100, mildOverMaxPercent = 1),
        )
    }

    @Test
    fun zero_budget_neutral_as_within() {
        assertEquals(HealthLevel.WITHIN, r.resolve(overspend = 1, totalBudget = 0))
    }
}
