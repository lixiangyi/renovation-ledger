package com.renovation.ledger.ui.paidgap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.metrics.PaidBudgetGap
import com.renovation.ledger.domain.metrics.PaidBudgetGapClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PaidGapDetailUiState(
    val overspendRows: List<PaidBudgetGap> = emptyList(),
    val surplusRows: List<PaidBudgetGap> = emptyList(),
)

@HiltViewModel
class PaidGapDetailViewModel @Inject constructor(
    projectRepository: ProjectRepository,
) : ViewModel() {

    val uiState = projectRepository.observeProjectWithItems()
        .map { (_, items) ->
            val (overspend, surplus) = PaidBudgetGapClassifier.classify(items)
            PaidGapDetailUiState(
                overspendRows = overspend,
                surplusRows = surplus,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PaidGapDetailUiState(),
        )
}
