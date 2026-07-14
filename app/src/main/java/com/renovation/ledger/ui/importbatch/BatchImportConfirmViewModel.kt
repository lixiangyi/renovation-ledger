package com.renovation.ledger.ui.importbatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.importing.ImportDraftStore
import com.renovation.ledger.domain.importing.ImportedLineDraft
import com.renovation.ledger.domain.importing.toBudgetItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatchImportUiState(
    val sourceLabel: String = "",
    val lines: List<ImportedLineDraft> = emptyList(),
    val selectedCount: Int = 0,
    val totalCount: Int = 0,
    val duplicateCount: Int = 0,
    val selectedSumCents: Long = 0L,
    val hint: String = "确认后将新建账本并切换过去；本 App 导出会还原预算/合同与付款，旧版导出仍为待购买",
    val isImporting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BatchImportConfirmViewModel @Inject constructor(
    private val importDraftStore: ImportDraftStore,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val lines = MutableStateFlow(importDraftStore.drafts)
    private val importing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState = combine(lines, importing, error) { current, isImporting, err ->
        toUiState(current, importDraftStore.sourceLabel, isImporting, err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = toUiState(
            importDraftStore.drafts,
            importDraftStore.sourceLabel,
            false,
            null,
        ),
    )

    fun toggleSelected(index: Int) {
        lines.update { list ->
            list.mapIndexed { i, draft ->
                if (i == index) draft.copy(selected = !draft.selected) else draft
            }
        }
    }

    fun selectAll() {
        lines.update { list -> list.map { it.copy(selected = true) } }
    }

    fun selectNonDuplicates() {
        lines.update { list ->
            list.map { it.copy(selected = !it.isDuplicate) }
        }
    }

    fun updateLine(index: Int, updater: (ImportedLineDraft) -> ImportedLineDraft) {
        lines.update { list ->
            list.mapIndexed { i, draft ->
                if (i == index) updater(draft) else draft
            }
        }
    }

    fun confirmImport(onDone: () -> Unit) {
        viewModelScope.launch {
            importing.value = true
            error.value = null
            try {
                val selected = lines.value.filter { it.selected }
                if (selected.isEmpty()) {
                    error.value = "请至少选择一行"
                    return@launch
                }
                // 导入写入新建账本，不污染当前账本
                val project = projectRepository.createProjectForImport()
                val items = selected.map { it.toBudgetItem(project.id) }
                projectRepository.upsertItems(items)
                importDraftStore.clear()
                onDone()
            } catch (e: Exception) {
                error.value = e.message ?: "导入失败"
            } finally {
                importing.value = false
            }
        }
    }

    private fun toUiState(
        current: List<ImportedLineDraft>,
        sourceLabel: String,
        isImporting: Boolean,
        err: String?,
    ): BatchImportUiState {
        val selected = current.filter { it.selected }
        return BatchImportUiState(
            sourceLabel = sourceLabel,
            lines = current,
            selectedCount = selected.size,
            totalCount = current.size,
            duplicateCount = current.count { it.isDuplicate },
            selectedSumCents = selected.sumOf { it.amountCents },
            isImporting = isImporting,
            error = err,
        )
    }
}
