package com.renovation.ledger.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.autosave.AutosaveSummary
import com.renovation.ledger.data.autosave.LedgerAutosave
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.metrics.HealthColorResolver
import com.renovation.ledger.domain.metrics.MetricsCalculator
import com.renovation.ledger.domain.model.HealthLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppShellUiState(
    val healthLevel: HealthLevel = HealthLevel.WITHIN,
    val healthColorEnabled: Boolean = true,
    val pendingAutosaveRestore: AutosaveSummary? = null,
    val restoreMessage: String? = null,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val ledgerAutosave: LedgerAutosave,
    userPrefs: UserPrefs,
    metricsCalculator: MetricsCalculator,
    healthColorResolver: HealthColorResolver,
) : ViewModel() {

    private val pendingAutosaveRestore = MutableStateFlow<AutosaveSummary?>(null)
    private val restoreMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        userPrefs.healthColorEnabled,
        userPrefs.mildOverMaxPercent,
        pendingAutosaveRestore,
        restoreMessage,
    ) { (_, items), enabled, mildPercent, pending, message ->
        val metrics = metricsCalculator.calculate(items)
        val level = healthColorResolver.resolve(
            overspend = metrics.projectedOverspend,
            totalBudget = metrics.totalBudget,
            mildOverMaxPercent = mildPercent,
        )
        AppShellUiState(
            healthLevel = level,
            healthColorEnabled = enabled,
            pendingAutosaveRestore = pending,
            restoreMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppShellUiState(),
    )

    init {
        viewModelScope.launch {
            // 先确保项目壳存在（有备份时不会种模板、不会冲 CSV）
            projectRepository.ensureDefaultProject()
            val (_, items) = projectRepository.observeProjectWithItems().first()
            if (items.isEmpty()) {
                val summary = ledgerAutosave.probeSummary()
                if (summary != null && summary.itemCount > 0) {
                    pendingAutosaveRestore.value = summary
                }
            }
        }
    }

    fun dismissAutosaveRestore() {
        pendingAutosaveRestore.value = null
    }

    fun confirmAutosaveRestore() {
        viewModelScope.launch {
            projectRepository.restoreFromAutosave()
                .onSuccess { summary ->
                    pendingAutosaveRestore.value = null
                    restoreMessage.value =
                        "已恢复 ${summary.itemCount} 项 / ${summary.paymentCount} 笔付款"
                }
                .onFailure { error ->
                    restoreMessage.value = error.message ?: "恢复失败"
                }
        }
    }

    fun clearRestoreMessage() {
        restoreMessage.value = null
    }
}
