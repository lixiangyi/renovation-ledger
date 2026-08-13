package com.renovation.ledger

import com.renovation.ledger.domain.list.PaymentListAggregator
import com.renovation.ledger.domain.list.PaymentListGroupBy
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentListAggregatorTest {

    @Test
    fun `group by category puts 灯具 under its own group`() {
        val items = listOf(
            item(
                "客厅主灯",
                stage = "软装",
                category = "灯具",
                budget = 0,
                payments = listOf(pay(3_200_00, PaymentStatus.PAID)),
            ),
            item("窗帘", stage = "软装", category = "软装", budget = 5_000_00),
        )
        val groups = PaymentListAggregator.group(items, PaymentListGroupBy.CATEGORY)
        assertEquals(setOf("灯具", "软装"), groups.map { it.key }.toSet())
        val lights = groups.first { it.key == "灯具" }
        assertEquals(3_200_00L, lights.paidSum)
        assertEquals(0L, lights.budgetSum)
        assertEquals(0L, lights.projectedSum)
    }

    @Test
    fun `paidSum is not effectiveCost when contract differs`() {
        val items = listOf(
            item(
                "橱柜",
                stage = "主材",
                category = "全屋定制",
                budget = 10_000_00,
                contract = 12_000_00,
                payments = listOf(
                    pay(5_000_00, PaymentStatus.PAID),
                    pay(7_000_00, PaymentStatus.UNPAID),
                ),
            ),
        )
        val g = PaymentListAggregator.group(items, PaymentListGroupBy.STAGE).single()
        assertEquals(5_000_00L, g.paidSum)
        assertEquals(10_000_00L, g.budgetSum)
        assertEquals(12_000_00L, g.projectedSum)
        assertEquals(1, g.paidItemCount)
        assertEquals(1, g.pendingItemCount)
        assertEquals(7_000_00L, g.pendingAmountSum)
    }

    @Test
    fun `new badge when budget zero and paid positive`() {
        val item = item(
            "临时灯",
            stage = "软装",
            category = "灯具",
            budget = 0,
            payments = listOf(pay(100_00, PaymentStatus.PAID)),
            isNew = false,
        )
        assertTrue(PaymentListAggregator.showNewBadge(item))
    }

    @Test
    fun `new badge when manual flag even if budget positive`() {
        val item = item(
            "中途加",
            stage = "软装",
            category = "软装",
            budget = 1_000_00,
            isNew = true,
        )
        assertTrue(PaymentListAggregator.showNewBadge(item))
        assertFalse(
            PaymentListAggregator.showNewBadge(
                item("普通", stage = "软装", category = "软装", budget = 1_000_00, isNew = false),
            ),
        )
    }

    @Test
    fun `group by space uses space key and blank becomes 未指定`() {
        val items = listOf(
            item("吊灯", stage = "软装", category = "灯具", space = "客厅", budget = 1_000_00),
            item("射灯", stage = "软装", category = "灯具", space = "客厅", budget = 500_00),
            item("未填空间项", stage = "软装", category = "软装", space = "", budget = 200_00),
        )
        val groups = PaymentListAggregator.group(items, PaymentListGroupBy.SPACE)
        assertEquals(setOf("客厅", "未指定"), groups.map { it.key }.toSet())
        assertEquals(2, groups.first { it.key == "客厅" }.items.size)
        assertEquals(1, groups.first { it.key == "未指定" }.items.size)
    }

    @Test
    fun `filter tab counts use item count and effectiveCost sum`() {
        val items = listOf(
            item("a", stage = "软装", category = "软装", budget = 1_000_00),
            item(
                "b",
                stage = "软装",
                category = "软装",
                budget = 2_000_00,
                payments = listOf(
                    pay(500_00, PaymentStatus.PAID),
                    pay(1_500_00, PaymentStatus.UNPAID),
                ),
            ),
        )
        val tabs = PaymentListAggregator.tabStats(items)
        assertEquals(2, tabs.all.count)
        assertEquals(1, tabs.toBuy.count)
        assertEquals(1_000_00L, tabs.toBuy.amountSum)
        assertEquals(1, tabs.paying.count)
        assertEquals(2_000_00L, tabs.paying.amountSum)
    }

    private fun item(
        name: String,
        stage: String,
        category: String,
        budget: Long,
        space: String = "",
        contract: Long? = null,
        payments: List<Payment> = emptyList(),
        isNew: Boolean = false,
    ) = BudgetItem(
        id = name,
        projectId = "p",
        name = name,
        stage = stage,
        category = category,
        space = space,
        budgetAmount = budget,
        contractAmount = contract,
        isNewAddition = isNew,
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
