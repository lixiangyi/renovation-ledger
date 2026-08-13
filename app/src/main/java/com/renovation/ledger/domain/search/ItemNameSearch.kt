package com.renovation.ledger.domain.search

import com.renovation.ledger.domain.model.BudgetItem

object ItemNameSearch {
    fun matchByName(items: List<BudgetItem>, query: String): List<BudgetItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return items.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun pushHistory(existing: List<String>, query: String, max: Int = 20): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return existing
        val without = existing.filterNot { it.equals(q, ignoreCase = true) }
        return (listOf(q) + without).take(max)
    }
}
