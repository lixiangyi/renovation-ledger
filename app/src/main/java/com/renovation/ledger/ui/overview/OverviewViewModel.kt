package com.renovation.ledger.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.remote.ApiErrorMessages
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.data.sync.LedgerSyncRepository
import com.renovation.ledger.domain.ledger.DeleteLedgerCopy
import com.renovation.ledger.domain.ledger.DeleteLedgerDialogCopy
import com.renovation.ledger.domain.ledger.LedgerOwnerDisplay
import com.renovation.ledger.domain.ledger.LedgerRoleGates
import com.renovation.ledger.domain.ledger.LedgerVisibility
import com.renovation.ledger.domain.ledger.VisibleLedger
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
import com.renovation.ledger.domain.model.effectiveCost
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

/** 总览页已花费 / 待花费展开态（存在 ViewModel，进出详情不丢失）。 */
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
    val category: String,
    val recordedDate: String?,
    val isNewAddition: Boolean,
    val budgetAmount: Long,
    val actualAmount: Long,
    val paidAmount: Long,
    val unpaidAmount: Long,
    val statusText: String,
)

data class OverviewUiState(
    val projectId: String = "",
    val projectName: String = "",
    val memberNames: String = "",
    val visibleLedgers: List<VisibleLedger> = emptyList(),
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
    val pendingBindPrompt: Pair<String, String>? = null,
    val profile: com.renovation.ledger.data.prefs.UserProfile =
        com.renovation.ledger.data.prefs.UserProfile(),
    val contentReady: Boolean = false,
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    private val metricsCalculator: MetricsCalculator,
    private val healthColorResolver: HealthColorResolver,
    private val ledgerSync: LedgerSyncRepository,
) : ViewModel() {

    fun pullFromCloud() {
        viewModelScope.launch {
            runCatching { ledgerSync.refreshOnOpen() }
                .onFailure { err ->
                    _userMessage.value = ApiErrorMessages.fromThrowable(err)
                }
        }
    }

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
            val project = projectRepository.observeProjects().first()
                .firstOrNull { it.id == projectId }
            if (userPrefs.jwt.first() != null && !project?.cloudLedgerId.isNullOrBlank()) {
                runCatching { ledgerSync.pullCurrent() }
                    .onFailure { err ->
                        _userMessage.value = ApiErrorMessages.fromThrowable(err)
                    }
            }
        }
    }

    fun confirmBindUpload() {
        viewModelScope.launch {
            runCatching {
                ledgerSync.importCurrent()
                userPrefs.clearPendingBindPrompt()
            }.onSuccess {
                _userMessage.value = "已上传到云端"
            }.onFailure {
                _userMessage.value = ApiErrorMessages.fromThrowable(it)
            }
        }
    }

    fun dismissBindPrompt() {
        viewModelScope.launch {
            userPrefs.clearPendingBindPrompt()
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val nickname = userPrefs.userProfile.first().nickname
            projectRepository.createProject(name = name, nickname = nickname)
            _expandUiState.value = OverviewExpandUiState()
            if (userPrefs.jwt.first() != null) {
                runCatching { ledgerSync.createCloudForCurrent() }
                    .onFailure {
                        _userMessage.value = "云端创建失败，账本仍在本机"
                    }
            }
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

    fun deleteDialogCopy(project: Project): DeleteLedgerDialogCopy {
        val role = LedgerRoleGates.roleOf(project.cloudLedgerId, ledgerSync.cloudSummaries.value)
        return DeleteLedgerCopy.forRole(
            role = role,
            ledgerName = project.name,
            hasCloudId = !project.cloudLedgerId.isNullOrBlank(),
        )
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.moveProjectToTrash(projectId)
                .onSuccess {
                    _userMessage.value = "已移入垃圾箱"
                    _expandUiState.value = OverviewExpandUiState()
                }
                .onFailure { err ->
                    val msg = err.message ?: "删除失败"
                    _userMessage.value = msg
                    if (msg.startsWith("已移入垃圾箱")) {
                        _expandUiState.value = OverviewExpandUiState()
                    }
                }
        }
    }

    private data class OverviewCore(
        val project: Project,
        val items: List<BudgetItem>,
        val projects: List<Project>,
        val healthColorEnabled: Boolean,
        val mildPercent: Int,
        val profile: com.renovation.ledger.data.prefs.UserProfile,
    )

    val uiState = combine(
        combine(
            projectRepository.observeProjectWithItems(),
            projectRepository.observeProjects(),
            userPrefs.healthColorEnabled,
            userPrefs.mildOverMaxPercent,
            userPrefs.userProfile,
        ) { projectWithItems, projects, healthColorEnabled, mildPercent, profile ->
            val (project, items) = projectWithItems
            OverviewCore(project, items, projects, healthColorEnabled, mildPercent, profile)
        },
        userPrefs.jwt,
        ledgerSync.cloudSummaries,
        userPrefs.pendingBindPrompt,
    ) { core, jwt, summaries, pendingBind ->
        val project = core.project
        val items = core.items
        val metrics = metricsCalculator.calculate(items)
        val currentHealth = healthColorResolver.resolve(
            metrics.currentOverspend,
            metrics.totalBudget,
            mildOverMaxPercent = core.mildPercent,
        )
        val projectedHealth = healthColorResolver.resolve(
            metrics.projectedOverspend,
            metrics.totalBudget,
            mildOverMaxPercent = core.mildPercent,
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
                    category = item.category.ifBlank { item.stage },
                    recordedDate = item.recordedDate,
                    isNewAddition = item.isNewAddition,
                    budgetAmount = item.budgetAmount,
                    actualAmount = item.effectiveCost(),
                    paidAmount = item.payments
                        .filter { it.status == PaymentStatus.PAID }
                        .sumOf { it.amount },
                    unpaidAmount = maxOf(
                        item.effectiveCost() - item.payments
                            .filter { it.status == PaymentStatus.PAID }
                            .sumOf { it.amount },
                        item.payments
                            .filter { it.status == PaymentStatus.UNPAID }
                            .sumOf { it.amount },
                    ).coerceAtLeast(0L),
                    statusText = when (item.deriveStatus()) {
                        ItemStatus.TO_BUY -> "待购买"
                        ItemStatus.PAYING -> "付款中"
                        ItemStatus.SETTLED -> "已结清"
                    },
                )
            }

        val members = LedgerOwnerDisplay.nickname(
            memberNames = project.memberNames.ifEmpty { listOf(core.profile.nickname) },
        )

        val visibleLedgers = LedgerVisibility.visible(
            projects = core.projects,
            cloudSummaries = summaries,
            loggedIn = !jwt.isNullOrBlank(),
        )

        OverviewUiState(
            projectId = project.id,
            projectName = project.name,
            memberNames = members,
            visibleLedgers = visibleLedgers,
            metrics = metrics,
            items = items,
            healthColorEnabled = core.healthColorEnabled,
            projectedHealth = projectedHealth,
            currentHealth = currentHealth,
            toBuyItems = toBuyItems,
            unpaidFinalRows = unpaidFinalRows,
            overspendRows = overspendRows,
            surplusRows = surplusRows,
            recentPayments = recentPayments,
            pendingBindPrompt = pendingBind,
            profile = core.profile,
            contentReady = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OverviewUiState(),
    )
}
