package com.renovation.ledger.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.model.effectiveCost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

enum class BudgetListFilter {
    ALL,
    TO_BUY,
    PAYING,
    SETTLED,
}

data class BudgetListItemUi(
    val item: BudgetItem,
    val status: ItemStatus,
    val paidSum: Long,
    val unpaidSum: Long,
)

data class BudgetListStageGroup(
    val stage: String,
    val items: List<BudgetListItemUi>,
    val budgetSum: Long,
    /** 实际花费：各项 effectiveCost（合同价优先）合计。 */
    val actualSum: Long,
    /** 实际 − 预算；正数超支，负数节余。 */
    val overspend: Long,
    /** 相对预算的超支率（可负）；预算为 0 时为 null。 */
    val overspendPercent: Int?,
    val health: HealthLevel,
    val expanded: Boolean,
)

data class BudgetListUiState(
    val filter: BudgetListFilter = BudgetListFilter.ALL,
    val groups: List<BudgetListStageGroup> = emptyList(),
    val mildOverMaxPercent: Int = HealthColorResolver.DEFAULT_MILD_OVER_MAX_PERCENT,
)

@HiltViewModel
class BudgetListViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    userPrefs: UserPrefs,
    private val healthColorResolver: HealthColorResolver,
) : ViewModel() {

    private val filter = MutableStateFlow(BudgetListFilter.ALL)

    /** 折叠态挂在 VM，进出详情只刷数据、不丢展开。 */
    private val expandedStages = MutableStateFlow<Set<String>>(emptySet())

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        filter,
        expandedStages,
        userPrefs.mildOverMaxPercent,
    ) { (_, items), currentFilter, expanded, mildPercent ->
        val filtered = items.filter { item ->
            when (currentFilter) {
                BudgetListFilter.ALL -> true
                BudgetListFilter.TO_BUY -> item.deriveStatus() == ItemStatus.TO_BUY
                BudgetListFilter.PAYING -> item.deriveStatus() == ItemStatus.PAYING
                BudgetListFilter.SETTLED -> item.deriveStatus() == ItemStatus.SETTLED
            }
        }
        val uiItems = filtered.map { item ->
            val paidSum = item.payments
                .filter { it.status == PaymentStatus.PAID }
                .sumOf { it.amount }
            val unpaidSum = item.payments
                .filter { it.status == PaymentStatus.UNPAID }
                .sumOf { it.amount }
            BudgetListItemUi(
                item = item,
                status = item.deriveStatus(),
                paidSum = paidSum,
                unpaidSum = unpaidSum,
            )
        }
        val groups = uiItems
            .groupBy { it.item.stage }
            .map { (stage, stageItems) ->
                val stageName = stage.ifBlank { "未分类" }
                val sortedItems = stageItems.sortedWith(
                    compareBy<BudgetListItemUi> { it.item.recordedDate.isNullOrBlank() }
                        .thenByDescending { it.item.recordedDate.orEmpty() }
                        .thenBy { it.item.name },
                )
                val budgetSum = sortedItems.sumOf { it.item.budgetAmount }
                val actualSum = sortedItems.sumOf { it.item.effectiveCost() }
                val overspend = actualSum - budgetSum
                val overspendPercent = if (budgetSum > 0L) {
                    ((overspend.toDouble() / budgetSum.toDouble()) * 100.0).roundToInt()
                } else {
                    null
                }
                BudgetListStageGroup(
                    stage = stageName,
                    items = sortedItems,
                    budgetSum = budgetSum,
                    actualSum = actualSum,
                    overspend = overspend,
                    overspendPercent = overspendPercent,
                    health = healthColorResolver.resolve(
                        overspend = overspend.coerceAtLeast(0L),
                        totalBudget = budgetSum,
                        mildOverMaxPercent = mildPercent,
                    ),
                    expanded = stageName in expanded,
                )
            }
            .sortedBy { it.stage }
        BudgetListUiState(
            filter = currentFilter,
            groups = groups,
            mildOverMaxPercent = mildPercent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetListUiState(),
    )

    fun setFilter(newFilter: BudgetListFilter) {
        filter.update { newFilter }
    }

    fun toggleStage(stage: String) {
        expandedStages.update { current ->
            if (stage in current) current - stage else current + stage
        }
    }
}

fun formatStageOverspendPercent(percent: Int?, overspend: Long): String = when {
    percent == null -> if (overspend > 0L) "超支 —" else "—"
    percent > 0 -> "超支 $percent%"
    percent < 0 -> "节余 ${abs(percent)}%"
    else -> "持平"
}
