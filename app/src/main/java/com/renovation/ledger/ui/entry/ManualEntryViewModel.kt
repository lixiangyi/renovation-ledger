package com.renovation.ledger.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.taxonomy.Taxonomy
import com.renovation.ledger.ui.detail.parseYuanToFen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ManualEntryMode {
    CHOOSE,
    NEW_ITEM,
    ADD_PAYMENT,
    EDIT_ITEM,
}

data class ManualEntryUiState(
    val mode: ManualEntryMode = ManualEntryMode.CHOOSE,
    val allItems: List<BudgetItem> = emptyList(),
    val projectId: String = "",
    val targetItemId: String = "",
    val editItemId: String = "",
    val saved: Boolean = false,
    val stages: List<String> = Taxonomy.STAGES,
    val categories: List<String> = Taxonomy.CATEGORIES,
    val spaces: List<String> = Taxonomy.SPACES,
)

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    taxonomyPrefs: com.renovation.ledger.data.prefs.TaxonomyPrefs,
) : ViewModel() {

    private val navItemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val navEditItemId: String = savedStateHandle.get<String>("editItemId").orEmpty()

    private val selectedMode = MutableStateFlow<ManualEntryMode?>(null)
    private val selectedItemId = MutableStateFlow("")
    private val savedFlag = MutableStateFlow(false)

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        selectedMode,
        selectedItemId,
        savedFlag,
        taxonomyPrefs.catalog,
    ) { (project, items), modeOverride, itemOverride, saved, catalog ->
        val initialMode = when {
            navEditItemId.isNotEmpty() -> ManualEntryMode.EDIT_ITEM
            navItemId.isNotEmpty() -> ManualEntryMode.ADD_PAYMENT
            else -> ManualEntryMode.CHOOSE
        }
        val mode = modeOverride ?: initialMode
        val targetItemId = when (mode) {
            ManualEntryMode.ADD_PAYMENT -> navItemId.ifEmpty { itemOverride }
            ManualEntryMode.EDIT_ITEM -> navEditItemId
            else -> itemOverride
        }
        ManualEntryUiState(
            mode = mode,
            allItems = items,
            projectId = project.id,
            targetItemId = targetItemId,
            editItemId = navEditItemId,
            saved = saved,
            stages = catalog.stages,
            categories = catalog.categories,
            spaces = catalog.spaces,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ManualEntryUiState(),
    )

    fun setMode(mode: ManualEntryMode) {
        selectedMode.value = mode
        savedFlag.value = false
    }

    fun setTargetItemId(itemId: String) {
        selectedItemId.value = itemId
    }

    fun createItem(
        name: String,
        stage: String,
        category: String,
        space: String,
        budgetYuan: String,
        isNewAddition: Boolean = true,
        onSuccess: () -> Unit,
    ) {
        val projectId = uiState.value.projectId
        val budgetFen = parseYuanToFen(budgetYuan) ?: return
        if (name.isBlank() || stage.isBlank()) return
        viewModelScope.launch {
            projectRepository.upsertItem(
                BudgetItem(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = name.trim(),
                    stage = stage.trim(),
                    category = category.trim(),
                    space = space.trim(),
                    budgetAmount = budgetFen,
                    isNewAddition = isNewAddition,
                ),
            )
            savedFlag.value = true
            onSuccess()
        }
    }

    fun addPayment(
        itemId: String,
        type: PaymentType,
        amountYuan: String,
        status: PaymentStatus,
        note: String,
        onSuccess: () -> Unit,
    ) {
        val amountFen = parseYuanToFen(amountYuan) ?: return
        if (itemId.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nickname = userPrefs.userProfile.first().nickname
            projectRepository.upsertPayment(
                Payment(
                    id = UUID.randomUUID().toString(),
                    budgetItemId = itemId,
                    type = type,
                    amount = amountFen,
                    status = status,
                    paidAtEpochMs = if (status == PaymentStatus.PAID) now else null,
                    note = note.trim(),
                    createdBy = nickname,
                ),
            )
            savedFlag.value = true
            onSuccess()
        }
    }

    fun updateItem(
        itemId: String,
        name: String,
        stage: String,
        category: String,
        space: String,
        budgetYuan: String,
        onSuccess: () -> Unit,
    ) {
        val item = uiState.value.allItems.find { it.id == itemId } ?: return
        val budgetFen = parseYuanToFen(budgetYuan) ?: return
        if (name.isBlank() || stage.isBlank()) return
        viewModelScope.launch {
            projectRepository.upsertItem(
                item.copy(
                    name = name.trim(),
                    stage = stage.trim(),
                    category = category.trim(),
                    space = space.trim(),
                    budgetAmount = budgetFen,
                ),
            )
            savedFlag.value = true
            onSuccess()
        }
    }
}
