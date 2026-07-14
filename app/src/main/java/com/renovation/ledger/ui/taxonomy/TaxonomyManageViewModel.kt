package com.renovation.ledger.ui.taxonomy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.domain.taxonomy.TaxonomyKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaxonomyManageUiState(
    val selectedKind: TaxonomyKind = TaxonomyKind.CATEGORY,
    val catalog: TaxonomyCatalog = TaxonomyCatalog(),
) {
    val options: List<String> get() = catalog.options(selectedKind)
}

@HiltViewModel
class TaxonomyManageViewModel @Inject constructor(
    private val taxonomyPrefs: TaxonomyPrefs,
) : ViewModel() {

    private val selectedKind = MutableStateFlow(TaxonomyKind.CATEGORY)

    val uiState = combine(
        taxonomyPrefs.catalog,
        selectedKind,
    ) { catalog, kind ->
        TaxonomyManageUiState(
            selectedKind = kind,
            catalog = catalog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaxonomyManageUiState(),
    )

    fun selectKind(kind: TaxonomyKind) {
        selectedKind.value = kind
    }

    fun add(value: String) {
        viewModelScope.launch {
            taxonomyPrefs.addOption(uiState.value.selectedKind, value)
        }
    }

    fun rename(oldValue: String, newValue: String) {
        viewModelScope.launch {
            taxonomyPrefs.renameOption(uiState.value.selectedKind, oldValue, newValue)
        }
    }

    fun remove(value: String) {
        viewModelScope.launch {
            taxonomyPrefs.removeOption(uiState.value.selectedKind, value)
        }
    }

    fun resetCurrent() {
        viewModelScope.launch {
            taxonomyPrefs.resetToDefaults(uiState.value.selectedKind)
        }
    }
}
