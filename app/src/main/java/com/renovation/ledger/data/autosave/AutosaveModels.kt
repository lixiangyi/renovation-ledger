package com.renovation.ledger.data.autosave

import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.Project

data class AutosaveSnapshot(
    val project: Project,
    val items: List<BudgetItem>,
    val payments: List<Payment>,
)

data class AutosaveSummary(
    val itemCount: Int,
    val paymentCount: Int,
)
