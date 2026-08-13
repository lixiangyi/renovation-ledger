package com.renovation.ledger

import com.renovation.ledger.domain.metrics.ProjectedSpendPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectedSpendPercentTest {

    @Test
    fun `projected over budget shows overspend percent`() {
        val result = ProjectedSpendPercent.compute(
            projectedTotal = 438_000_00,
            totalBudget = 420_000_00,
        )
        assertEquals(4, result.percent)
        assertEquals(18_000_00L, result.gap)
        assertTrue(result.label.contains("预计超支"))
        assertTrue(result.label.contains("4%"))
    }

    @Test
    fun `zero budget returns null percent and dash label`() {
        val result = ProjectedSpendPercent.compute(
            projectedTotal = 100_000_00,
            totalBudget = 0,
        )
        assertNull(result.percent)
        assertEquals(100_000_00L, result.gap)
        assertEquals("—", result.label)
    }

    @Test
    fun `projected under budget shows savings label`() {
        val result = ProjectedSpendPercent.compute(
            projectedTotal = 380_000_00,
            totalBudget = 420_000_00,
        )
        assertEquals(-10, result.percent)
        assertEquals(-40_000_00L, result.gap)
        assertTrue(result.label.contains("预计节省"))
        assertTrue(result.label.contains("10%"))
    }

    @Test
    fun `projected equals budget shows flat label`() {
        val result = ProjectedSpendPercent.compute(
            projectedTotal = 420_000_00,
            totalBudget = 420_000_00,
        )
        assertEquals(0, result.percent)
        assertEquals(0L, result.gap)
        assertEquals("持平", result.label)
    }
}
