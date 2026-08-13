package com.renovation.ledger.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.search.ItemNameSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchGuideUiState(
    val query: String = "",
    val results: List<BudgetItem> = emptyList(),
    val history: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchGuideViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val currentItems = projectRepository.observeProjectWithItems()
        .map { (_, items) -> items }

    val uiState: kotlinx.coroutines.flow.StateFlow<SearchGuideUiState> = combine(
        _query,
        currentItems,
        userPrefs.searchHistory,
    ) { query, items, history ->
        SearchGuideUiState(
            query = query,
            results = ItemNameSearch.matchByName(items, query),
            history = history,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchGuideUiState(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** 提交搜索（如点击键盘搜索键、或点击结果项）：写入历史。 */
    fun onSubmit() {
        val trimmed = _query.value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { userPrefs.addSearchHistory(trimmed) }
    }

    /** 点击历史 chip：回填查询词并写入历史（提到最前）。 */
    fun selectHistory(value: String) {
        _query.value = value
        viewModelScope.launch { userPrefs.addSearchHistory(value) }
    }

    fun clearHistory() {
        viewModelScope.launch { userPrefs.clearSearchHistory() }
    }
}
