package com.renovation.ledger.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.list.FilterTabStats
import com.renovation.ledger.domain.list.PaymentListAggregator
import com.renovation.ledger.domain.list.PaymentListGroupBy
import com.renovation.ledger.domain.list.PaymentListLayout
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.taxonomy.TaxonomyIconRef
import com.renovation.ledger.domain.taxonomy.TaxonomyKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val showNewBadge: Boolean,
)

data class BudgetListStageGroup(
    /** 分组显示 key（按 stage 或 category 分组时的组名）。 */
    val stage: String,
    val items: List<BudgetListItemUi>,
    val budgetSum: Long,
    /** 已支付：Σ 已付付款。 */
    val paidSum: Long,
    /** 预计要支付：Σ effectiveCost（合同价优先）。 */
    val projectedSum: Long,
    val paidItemCount: Int,
    val pendingItemCount: Int,
    val pendingAmountSum: Long,
    /** 预计 − 预算；正数超支，负数节余。 */
    val overspend: Long,
    /** 相对预算的超支率（可负）；预算为 0 时为 null。 */
    val overspendPercent: Int?,
    val health: HealthLevel,
    val expanded: Boolean,
    val icon: TaxonomyIconRef? = null,
)

data class BudgetListUiState(
    val filter: BudgetListFilter = BudgetListFilter.ALL,
    val groups: List<BudgetListStageGroup> = emptyList(),
    val mildOverMaxPercent: Int = HealthColorResolver.DEFAULT_MILD_OVER_MAX_PERCENT,
    val groupBy: PaymentListGroupBy = PaymentListGroupBy.STAGE,
    val layout: PaymentListLayout = PaymentListLayout.NESTED,
    val tabStats: FilterTabStats = PaymentListAggregator.tabStats(emptyList()),
)

private data class CombinedFilters(
    val filter: BudgetListFilter,
    val expanded: Set<String>,
    val mildPercent: Int,
    val groupBy: PaymentListGroupBy,
)

@HiltViewModel
class BudgetListViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val healthColorResolver: HealthColorResolver,
    private val taxonomyPrefs: TaxonomyPrefs,
) : ViewModel() {

    private val filter = MutableStateFlow(BudgetListFilter.ALL)

    /** 折叠态挂在 VM，进出详情只刷数据、不丢展开。 */
    private val expandedStages = MutableStateFlow<Set<String>>(emptySet())

    private val combinedFilters = combine(
        filter,
        expandedStages,
        userPrefs.mildOverMaxPercent,
        userPrefs.paymentListGroupBy,
    ) { currentFilter, expanded, mildPercent, groupBy ->
        CombinedFilters(currentFilter, expanded, mildPercent, groupBy)
    }

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        combinedFilters,
        userPrefs.paymentListLayout,
        taxonomyPrefs.catalog,
    ) { (_, items), combinedFilter, layout, catalog ->
        val (currentFilter, expanded, mildPercent, groupBy) = combinedFilter
        val filtered = items.filter { item ->
            when (currentFilter) {
                BudgetListFilter.ALL -> true
                BudgetListFilter.TO_BUY -> item.deriveStatus() == ItemStatus.TO_BUY
                BudgetListFilter.PAYING -> item.deriveStatus() == ItemStatus.PAYING
                BudgetListFilter.SETTLED -> item.deriveStatus() == ItemStatus.SETTLED
            }
        }
        val groups = PaymentListAggregator.group(filtered, groupBy)
            .map { metrics ->
                val sortedItems = metrics.items
                    .sortedWith(
                        compareBy<BudgetItem> { it.recordedDate.isNullOrBlank() }
                            .thenByDescending { it.recordedDate.orEmpty() }
                            .thenBy { it.name },
                    )
                    .map { item -> item.toUi() }
                val overspend = metrics.projectedSum - metrics.budgetSum
                val overspendPercent = if (metrics.budgetSum > 0L) {
                    ((overspend.toDouble() / metrics.budgetSum.toDouble()) * 100.0).roundToInt()
                } else {
                    null
                }
                val taxonomyKind = when (groupBy) {
                    PaymentListGroupBy.STAGE -> TaxonomyKind.STAGE
                    PaymentListGroupBy.CATEGORY -> TaxonomyKind.CATEGORY
                    PaymentListGroupBy.SPACE -> TaxonomyKind.SPACE
                }
                BudgetListStageGroup(
                    stage = metrics.key,
                    items = sortedItems,
                    budgetSum = metrics.budgetSum,
                    paidSum = metrics.paidSum,
                    projectedSum = metrics.projectedSum,
                    paidItemCount = metrics.paidItemCount,
                    pendingItemCount = metrics.pendingItemCount,
                    pendingAmountSum = metrics.pendingAmountSum,
                    overspend = overspend,
                    overspendPercent = overspendPercent,
                    health = healthColorResolver.resolve(
                        overspend = overspend.coerceAtLeast(0L),
                        totalBudget = metrics.budgetSum,
                        mildOverMaxPercent = mildPercent,
                    ),
                    expanded = metrics.key in expanded,
                    icon = catalog.iconFor(taxonomyKind, metrics.key),
                )
            }
        BudgetListUiState(
            filter = currentFilter,
            groups = groups,
            mildOverMaxPercent = mildPercent,
            groupBy = groupBy,
            layout = layout,
            tabStats = PaymentListAggregator.tabStats(items),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetListUiState(),
    )

    private fun BudgetItem.toUi(): BudgetListItemUi {
        val paidSum = payments
            .filter { it.status == PaymentStatus.PAID }
            .sumOf { it.amount }
        val unpaidSum = payments
            .filter { it.status == PaymentStatus.UNPAID }
            .sumOf { it.amount }
        return BudgetListItemUi(
            item = this,
            status = deriveStatus(),
            paidSum = paidSum,
            unpaidSum = unpaidSum,
            showNewBadge = PaymentListAggregator.showNewBadge(this),
        )
    }

    fun setFilter(newFilter: BudgetListFilter) {
        filter.update { newFilter }
    }

    fun toggleStage(stage: String) {
        expandedStages.update { current ->
            if (stage in current) current - stage else current + stage
        }
    }

    fun setGroupBy(groupBy: PaymentListGroupBy) {
        viewModelScope.launch {
            userPrefs.setPaymentListGroupBy(groupBy)
        }
    }

    fun setLayout(layout: PaymentListLayout) {
        viewModelScope.launch {
            userPrefs.setPaymentListLayout(layout)
        }
    }
}

fun formatStageOverspendPercent(percent: Int?, overspend: Long): String = when {
    percent == null -> if (overspend > 0L) "超支 —" else "—"
    percent > 0 -> "超支 $percent%"
    percent < 0 -> "节余 ${abs(percent)}%"
    else -> "持平"
}
