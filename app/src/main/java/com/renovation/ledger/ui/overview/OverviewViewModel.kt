package com.renovation.ledger.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.metrics.MetricsCalculator
import com.renovation.ledger.domain.metrics.PaidBudgetGapClassifier
import com.renovation.ledger.domain.metrics.ProjectMetrics
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.HealthLevel
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.Project
import com.renovation.ledger.domain.model.deriveStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 总览页已实付 / 待花费展开态（存在 ViewModel，进出详情不丢失）。 */
data class OverviewExpandUiState(
    val paidExpanded: Boolean = false,
    val pendingExpanded: Boolean = false,
    val paidTab: Int = 0,
    val pendingTab: Int = 0,
)

data class UnpaidFinalRow(
    val itemId: String,
    val itemName: String,
    val unpaidAmount: Long,
)

/** 单项已付相对预算的超支或节余。 */
data class PaidBudgetGapRow(
    val itemId: String,
    val itemName: String,
    val gapAmount: Long,
)

data class RecentPaymentRow(
    val payment: Payment,
    val itemId: String,
    val itemName: String,
    val budgetAmount: Long,
)

data class OverviewUiState(
    val projectId: String = "",
    val projectName: String = "",
    val memberNames: String = "",
    val projects: List<Project> = emptyList(),
    val metrics: ProjectMetrics = ProjectMetrics(
        totalBudget = 0L,
        paidActual = 0L,
        unpaidFinal = 0L,
        toBuyAmount = 0L,
        pendingSpend = 0L,
        currentOverspend = 0L,
        projectedTotal = 0L,
        projectedOverspend = 0L,
    ),
    val items: List<BudgetItem> = emptyList(),
    val healthColorEnabled: Boolean = true,
    val projectedHealth: HealthLevel = HealthLevel.WITHIN,
    val currentHealth: HealthLevel = HealthLevel.WITHIN,
    val toBuyItems: List<BudgetItem> = emptyList(),
    val unpaidFinalRows: List<UnpaidFinalRow> = emptyList(),
    val overspendRows: List<PaidBudgetGapRow> = emptyList(),
    val surplusRows: List<PaidBudgetGapRow> = emptyList(),
    val recentPayments: List<RecentPaymentRow> = emptyList(),
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val metricsCalculator: MetricsCalculator,
    private val healthColorResolver: HealthColorResolver,
) : ViewModel() {

    private val _expandUiState = MutableStateFlow(OverviewExpandUiState())
    val expandUiState = _expandUiState.asStateFlow()

    fun togglePaidExpanded() {
        _expandUiState.update { cur ->
            val next = !cur.paidExpanded
            cur.copy(
                paidExpanded = next,
                pendingExpanded = if (next) false else cur.pendingExpanded,
            )
        }
    }

    fun togglePendingExpanded() {
        _expandUiState.update { cur ->
            val next = !cur.pendingExpanded
            cur.copy(
                pendingExpanded = next,
                paidExpanded = if (next) false else cur.paidExpanded,
            )
        }
    }

    fun setPaidTab(tab: Int) {
        _expandUiState.update { it.copy(paidTab = tab) }
    }

    fun setPendingTab(tab: Int) {
        _expandUiState.update { it.copy(pendingTab = tab) }
    }

    fun switchProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.switchProject(projectId)
            _expandUiState.value = OverviewExpandUiState()
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val nickname = userPrefs.userProfile.first().nickname
            projectRepository.createProject(name = name, nickname = nickname)
            _expandUiState.value = OverviewExpandUiState()
        }
    }

    fun renameProject(projectId: String, name: String) {
        viewModelScope.launch {
            projectRepository.renameProject(projectId, name)
        }
    }

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.moveProjectToTrash(projectId)
                .onSuccess {
                    _userMessage.value = "已移入垃圾箱"
                    _expandUiState.value = OverviewExpandUiState()
                }
                .onFailure { err ->
                    _userMessage.value = err.message ?: "删除失败"
                }
        }
    }

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        projectRepository.observeProjects(),
        userPrefs.healthColorEnabled,
        userPrefs.mildOverMaxPercent,
        userPrefs.userProfile,
    ) { (project, items), projects, healthColorEnabled, mildPercent, profile ->
        val metrics = metricsCalculator.calculate(items)
        val currentHealth = healthColorResolver.resolve(
            metrics.currentOverspend,
            metrics.totalBudget,
            mildOverMaxPercent = mildPercent,
        )
        val projectedHealth = healthColorResolver.resolve(
            metrics.projectedOverspend,
            metrics.totalBudget,
            mildOverMaxPercent = mildPercent,
        )
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
        val (overspend, surplus) = PaidBudgetGapClassifier.classify(items)
        val overspendRows = overspend.map {
            PaidBudgetGapRow(it.itemId, it.itemName, it.gapAmount)
        }
        val surplusRows = surplus.map {
            PaidBudgetGapRow(it.itemId, it.itemName, it.gapAmount)
        }
        val recentPayments = items
            .flatMap { item -> item.payments.map { payment -> Triple(payment, item.id, item) } }
            .sortedByDescending { (payment, _, _) -> payment.paidAtEpochMs ?: 0L }
            .take(5)
            .map { (payment, itemId, item) ->
                RecentPaymentRow(
                    payment = payment,
                    itemId = itemId,
                    itemName = item.name,
                    budgetAmount = item.budgetAmount,
                )
            }

        val members = project.memberNames
            .ifEmpty { listOf(profile.nickname) }
            .joinToString(" & ")

        OverviewUiState(
            projectId = project.id,
            projectName = project.name,
            memberNames = members,
            projects = projects,
            metrics = metrics,
            items = items,
            healthColorEnabled = healthColorEnabled,
            projectedHealth = projectedHealth,
            currentHealth = currentHealth,
            toBuyItems = toBuyItems,
            unpaidFinalRows = unpaidFinalRows,
            overspendRows = overspendRows,
            surplusRows = surplusRows,
            recentPayments = recentPayments,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewUiState(),
    )
}
