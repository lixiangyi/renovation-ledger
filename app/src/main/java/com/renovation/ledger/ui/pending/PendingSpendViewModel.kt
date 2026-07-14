package com.renovation.ledger.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.ui.overview.UnpaidFinalRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PendingSpendUiState(
    val unpaidFinalRows: List<UnpaidFinalRow> = emptyList(),
    val toBuyItems: List<BudgetItem> = emptyList(),
)

@HiltViewModel
class PendingSpendViewModel @Inject constructor(
    projectRepository: ProjectRepository,
) : ViewModel() {

    val uiState = projectRepository.observeProjectWithItems()
        .map { (_, items) ->
            val toBuyItems = items.filter { it.deriveStatus() == ItemStatus.TO_BUY }
            val unpaidFinalRows = items
                .filter { it.deriveStatus() == ItemStatus.PAYING }
                .mapNotNull { item ->
                    val unpaid = item.payments
                        .filter { it.status == PaymentStatus.UNPAID }
                        .sumOf { it.amount }
                    if (unpaid > 0L) {
                        UnpaidFinalRow(
                            itemId = item.id,
                            itemName = item.name,
                            unpaidAmount = unpaid,
                        )
                    } else {
                        null
                    }
                }
            PendingSpendUiState(
                unpaidFinalRows = unpaidFinalRows,
                toBuyItems = toBuyItems,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PendingSpendUiState(),
        )
}
