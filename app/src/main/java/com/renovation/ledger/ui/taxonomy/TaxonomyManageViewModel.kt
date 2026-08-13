package com.renovation.ledger.ui.taxonomy

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.data.taxonomy.TaxonomyIconStorage
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import com.renovation.ledger.domain.taxonomy.TaxonomyIconRef
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
    private val taxonomyIconStorage: TaxonomyIconStorage,
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

    /** 相册取图后立即落盘，返回本地路径供对话框内即时预览。 */
    fun saveIconFile(uri: Uri): String? = runCatching { taxonomyIconStorage.saveFromUri(uri) }.getOrNull()

    fun add(value: String, icon: TaxonomyIconRef?) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val kind = uiState.value.selectedKind
            taxonomyPrefs.addOption(kind, trimmed)
            taxonomyPrefs.setIcon(kind, trimmed, icon)
        }
    }

    fun rename(oldValue: String, newValue: String, icon: TaxonomyIconRef?) {
        val trimmed = newValue.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val kind = uiState.value.selectedKind
            val oldIcon = uiState.value.catalog.iconFor(kind, oldValue)
            taxonomyPrefs.renameOption(kind, oldValue, trimmed)
            if (icon != oldIcon) {
                taxonomyPrefs.setIcon(kind, trimmed, icon)
                val replacedCustomFile = oldIcon?.iconPath
                if (!replacedCustomFile.isNullOrBlank() && replacedCustomFile != icon?.iconPath) {
                    taxonomyIconStorage.delete(replacedCustomFile)
                }
            }
        }
    }

    fun remove(value: String) {
        viewModelScope.launch {
            val kind = uiState.value.selectedKind
            val icon = uiState.value.catalog.iconFor(kind, value)
            taxonomyPrefs.removeOption(kind, value)
            icon?.iconPath?.let { taxonomyIconStorage.delete(it) }
        }
    }

    fun resetCurrent() {
        viewModelScope.launch {
            taxonomyPrefs.resetToDefaults(uiState.value.selectedKind)
        }
    }
}
