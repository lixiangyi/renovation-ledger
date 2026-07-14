package com.renovation.ledger

import com.renovation.ledger.domain.metrics.MetricsCalculator
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsCalculatorTest {
    private val calc = MetricsCalculator()
    private fun y(yuan: Long) = yuan * 100L

    @Test
    fun calculates_paid_unpaid_tobuy_and_projected() {
        val items = listOf(
            BudgetItem(
                id = "1", projectId = "p", name = "大项", stage = "泥木",
                budgetAmount = y(200_000),
                contractAmount = y(430_000),
                payments = listOf(
                    Payment("p1", "1", PaymentType.DEPOSIT, y(430_000), PaymentStatus.PAID)
                )
            ),
            BudgetItem(
                id = "2", projectId = "p", name = "尾款项", stage = "泥木",
                budgetAmount = y(100_000),
                contractAmount = y(50_000),
                payments = listOf(
                    Payment("p2", "2", PaymentType.FINAL, y(50_000), PaymentStatus.UNPAID)
                )
            ),
            BudgetItem(
                id = "3", projectId = "p", name = "待买", stage = "软装",
                budgetAmount = y(100_000)
            )
        )
        val m = calc.calculate(items)
        assertEquals(y(400_000), m.totalBudget)
        assertEquals(y(430_000), m.paidActual)
        assertEquals(y(50_000), m.unpaidFinal)
        assertEquals(y(100_000), m.toBuyAmount)
        assertEquals(y(150_000), m.pendingSpend)
        assertEquals(y(580_000), m.projectedTotal)
        assertEquals(y(30_000), m.currentOverspend)
        assertEquals(y(180_000), m.projectedOverspend)
    }
}
