package com.renovation.ledger

import com.renovation.ledger.domain.metrics.UnpaidCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class UnpaidCalculatorTest {

    @Test
    fun `display unpaid uses contract minus paid when contract set`() {
        assertEquals(7_000_00L, UnpaidCalculator.displayUnpaid(contract = 12_000_00, paid = 5_000_00))
        assertEquals(0L, UnpaidCalculator.displayUnpaid(contract = 5_000_00, paid = 5_000_00))
        assertEquals(0L, UnpaidCalculator.displayUnpaid(contract = 3_000_00, paid = 5_000_00))
    }

    @Test
    fun `without contract fall back to unpaid payment rows sum`() {
        assertEquals(
            2_000_00L,
            UnpaidCalculator.displayUnpaid(contract = null, paid = 1_000_00, unpaidRowsSum = 2_000_00),
        )
    }

    @Test
    fun `suggest unpaid remainder for new unpaid row`() {
        assertEquals(7_000_00L, UnpaidCalculator.suggestUnpaidAmount(contract = 12_000_00, paid = 5_000_00))
        assertEquals(0L, UnpaidCalculator.suggestUnpaidAmount(contract = null, paid = 5_000_00))
    }
}
