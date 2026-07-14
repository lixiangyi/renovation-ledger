package com.renovation.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.metrics.GroupBy
import com.renovation.ledger.domain.metrics.GroupMetrics
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.metrics.PieMetric
import com.renovation.ledger.domain.metrics.aggregate
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ContractGapRow(
    val itemId: String,
    val itemName: String,
    val budget: Long,
    val contract: Long,
    val gap: Long,
)

data class StatsUiState(
    val groupBy: GroupBy = GroupBy.CATEGORY,
    val pieMetric: PieMetric = PieMetric.PROJECTED,
    val groups: List<GroupMetrics> = emptyList(),
    val topContractGaps: List<ContractGapRow> = emptyList(),
    val healthColorEnabled: Boolean = true,
    val mildOverMaxPercent: Int = HealthColorResolver.DEFAULT_MILD_OVER_MAX_PERCENT,
    val items: List<BudgetItem> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val healthColorResolver: HealthColorResolver,
) : ViewModel() {

    private val groupBy = MutableStateFlow(GroupBy.CATEGORY)
    private val pieMetric = MutableStateFlow(PieMetric.PROJECTED)

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        userPrefs.healthColorEnabled,
        userPrefs.mildOverMaxPercent,
        groupBy,
        pieMetric,
    ) { (_, items), healthColorEnabled, mildPercent, currentGroupBy, currentPieMetric ->
        StatsUiState(
            groupBy = currentGroupBy,
            pieMetric = currentPieMetric,
            groups = aggregate(items, currentGroupBy),
            topContractGaps = items
                .filter { it.contractAmount != null }
                .map { item ->
                    val contract = item.contractAmount!!
                    ContractGapRow(
                        itemId = item.id,
                        itemName = item.name,
                        budget = item.budgetAmount,
                        contract = contract,
                        gap = contract - item.budgetAmount,
                    )
                }
                .sortedByDescending { it.gap }
                .take(5),
            healthColorEnabled = healthColorEnabled,
            mildOverMaxPercent = mildPercent,
            items = items,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(groupBy = GroupBy.CATEGORY),
    )

    fun setGroupBy(value: GroupBy) {
        groupBy.value = value
    }

    fun setPieMetric(value: PieMetric) {
        pieMetric.value = value
    }

    fun overspendHealth(overspend: Long, budget: Long): HealthLevel =
        healthColorResolver.resolve(
            overspend,
            budget,
            mildOverMaxPercent = uiState.value.mildOverMaxPercent,
        )
}

fun GroupMetrics.overspend(): Long = projected - budget

fun GroupMetrics.pieValue(metric: PieMetric): Long = when (metric) {
    PieMetric.PAID -> paid
    PieMetric.PROJECTED -> projected
    PieMetric.BUDGET -> budget
}
