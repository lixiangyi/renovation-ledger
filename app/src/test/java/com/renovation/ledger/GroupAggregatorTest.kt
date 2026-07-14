package com.renovation.ledger

import com.renovation.ledger.domain.metrics.GroupBy
import com.renovation.ledger.domain.metrics.aggregate
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupAggregatorTest {
    private fun y(yuan: Long) = yuan * 100L

    @Test
    fun aggregates_two_items_in_different_stages() {
        val items = listOf(
            BudgetItem(
                id = "1",
                projectId = "p",
                name = "水电改造",
                stage = "水电",
                budgetAmount = y(10_000),
                contractAmount = y(12_000),
                payments = listOf(
                    Payment("p1", "1", PaymentType.DEPOSIT, y(5_000), PaymentStatus.PAID),
                ),
            ),
            BudgetItem(
                id = "2",
                projectId = "p",
                name = "泥木施工",
                stage = "泥木",
                budgetAmount = y(20_000),
                payments = listOf(
                    Payment("p2", "2", PaymentType.FINAL, y(3_000), PaymentStatus.UNPAID),
                ),
            ),
        )

        val result = aggregate(items, GroupBy.STAGE)
        assertEquals(2, result.size)

        val shuidian = result.first { it.key == "水电" }
        assertEquals(y(10_000), shuidian.budget)
        assertEquals(y(5_000), shuidian.paid)
        assertEquals(y(12_000), shuidian.projected)

        val nimu = result.first { it.key == "泥木" }
        assertEquals(y(20_000), nimu.budget)
        assertEquals(0L, nimu.paid)
        assertEquals(y(20_000), nimu.projected)
    }

    @Test
    fun blank_category_falls_back_to_stage() {
        val items = listOf(
            BudgetItem(
                id = "1",
                projectId = "p",
                name = "沙发",
                stage = "家具",
                category = "",
                budgetAmount = y(1_000),
            ),
        )
        val result = aggregate(items, GroupBy.CATEGORY)
        assertEquals(1, result.size)
        assertEquals("家具", result.first().key)
        assertEquals(y(1_000), result.first().budget)
    }

    @Test
    fun blank_category_and_stage_becomes_uncategorized() {
        val items = listOf(
            BudgetItem(
                id = "1",
                projectId = "p",
                name = "无分类项",
                stage = "",
                category = "",
                budgetAmount = y(1_000),
            ),
        )
        val result = aggregate(items, GroupBy.CATEGORY)
        assertEquals(1, result.size)
        assertEquals("未分类", result.first().key)
        assertEquals(y(1_000), result.first().budget)
    }
}
