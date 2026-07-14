package com.renovation.ledger

import com.renovation.ledger.domain.metrics.PaidBudgetGapClassifier
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidBudgetGapClassifierTest {

    @Test
    fun `paying with unpaid final is not surplus even if paid under budget`() {
        val item = item(
            name = "全屋净水",
            budget = 22_100_00,
            payments = listOf(
                pay(6_000_00, PaymentStatus.PAID),
                pay(16_100_00, PaymentStatus.UNPAID),
            ),
        )
        val (overspend, surplus) = PaidBudgetGapClassifier.classify(listOf(item))
        assertTrue(overspend.isEmpty())
        assertTrue(surplus.isEmpty())
    }

    @Test
    fun `settled under budget counts as surplus`() {
        val item = item(
            name = "窗帘",
            budget = 10_000_00,
            payments = listOf(pay(8_000_00, PaymentStatus.PAID)),
            contract = 8_000_00,
        )
        val (overspend, surplus) = PaidBudgetGapClassifier.classify(listOf(item))
        assertTrue(overspend.isEmpty())
        assertEquals(1, surplus.size)
        assertEquals(2_000_00L, surplus[0].gapAmount)
        assertEquals(10_000_00L, surplus[0].budgetAmount)
        assertEquals(8_000_00L, surplus[0].paidAmount)
        assertEquals(20, surplus[0].gapPercent)
    }

    @Test
    fun `paid over budget is overspend even while paying`() {
        val item = item(
            name = "橱柜",
            budget = 10_000_00,
            payments = listOf(
                pay(12_000_00, PaymentStatus.PAID),
                pay(3_000_00, PaymentStatus.UNPAID),
            ),
        )
        val (overspend, surplus) = PaidBudgetGapClassifier.classify(listOf(item))
        assertEquals(1, overspend.size)
        assertEquals(2_000_00L, overspend[0].gapAmount)
        assertEquals(10_000_00L, overspend[0].budgetAmount)
        assertEquals(12_000_00L, overspend[0].paidAmount)
        assertEquals(20, overspend[0].gapPercent)
        assertTrue(surplus.isEmpty())
    }

    private fun item(
        name: String,
        budget: Long,
        payments: List<Payment>,
        contract: Long? = null,
    ) = BudgetItem(
        id = "id-$name",
        projectId = "p",
        name = name,
        stage = "其他",
        budgetAmount = budget,
        contractAmount = contract,
        payments = payments,
    )

    private fun pay(amount: Long, status: PaymentStatus) = Payment(
        id = "pay-$amount-$status",
        budgetItemId = "x",
        type = PaymentType.FINAL,
        amount = amount,
        status = status,
        paidAtEpochMs = if (status == PaymentStatus.PAID) 1L else null,
    )
}
