package com.renovation.ledger

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.search.ItemNameSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemNameSearchTest {
    private fun item(name: String) = BudgetItem(
        id = name,
        projectId = "p",
        name = name,
        stage = "水电",
        budgetAmount = 100,
    )

    @Test
    fun matchByName_filters_case_insensitive_contains() {
        val items = listOf(
            item("厨房橱柜"),
            item("卫生间瓷砖"),
            item("客厅地板"),
        )

        val result = ItemNameSearch.matchByName(items, "厨")

        assertEquals(1, result.size)
        assertEquals("厨房橱柜", result[0].name)
    }

    @Test
    fun matchByName_empty_query_returns_empty() {
        val items = listOf(item("厨房橱柜"))

        assertTrue(ItemNameSearch.matchByName(items, "").isEmpty())
        assertTrue(ItemNameSearch.matchByName(items, "   ").isEmpty())
    }

    @Test
    fun pushHistory_prepends_and_dedupes_case_insensitive() {
        val existing = listOf("瓷砖", "橱柜", "tile")

        val result = ItemNameSearch.pushHistory(existing, "  Tile  ")

        assertEquals(listOf("Tile", "瓷砖", "橱柜"), result)
    }

    @Test
    fun pushHistory_caps_at_20() {
        val existing = (1..25).map { "item-$it" }

        val result = ItemNameSearch.pushHistory(existing, "new-query")

        assertEquals(20, result.size)
        assertEquals("new-query", result.first())
        assertEquals("item-19", result.last())
    }

    @Test
    fun pushHistory_blank_query_returns_existing() {
        val existing = listOf("橱柜", "瓷砖")

        assertEquals(existing, ItemNameSearch.pushHistory(existing, ""))
        assertEquals(existing, ItemNameSearch.pushHistory(existing, "   "))
    }
}
