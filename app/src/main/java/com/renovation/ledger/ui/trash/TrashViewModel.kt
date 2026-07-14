package com.renovation.ledger.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.data.trash.TrashEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashUiState(
    val entries: List<TrashEntry> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = projectRepository.listTrash()
            _uiState.update { it.copy(entries = entries) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun restore(entryId: String) {
        viewModelScope.launch {
            projectRepository.restoreFromTrash(entryId)
                .onSuccess {
                    _uiState.update { it.copy(message = "已恢复并切换到该账本") }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(message = err.message ?: "恢复失败") }
                }
        }
    }

    fun purge(entryId: String) {
        viewModelScope.launch {
            projectRepository.purgeTrashEntry(entryId)
                .onSuccess {
                    _uiState.update { it.copy(message = "已永久删除") }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(message = err.message ?: "删除失败") }
                }
        }
    }
}
