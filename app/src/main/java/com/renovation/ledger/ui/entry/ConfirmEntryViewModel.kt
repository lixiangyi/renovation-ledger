package com.renovation.ledger.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.Payment
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.taxonomy.Taxonomy
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
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

enum class ConfirmActionType {
    NEW_ITEM,
    ADD_PAYMENT,
}

data class ConfirmEntryDraft(
    val actionType: ConfirmActionType = ConfirmActionType.ADD_PAYMENT,
    val itemName: String = "",
    val selectedItemId: String = "",
    val amountYuan: String = "",
    val paymentType: PaymentType = PaymentType.FINAL,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val budgetYuan: String = "",
    val stage: String = Taxonomy.STAGES.first(),
    val category: String = Taxonomy.CATEGORIES.first(),
    val space: String = "",
    val showRecognitionBanner: Boolean = false,
)

data class ConfirmEntryUiState(
    val draft: ConfirmEntryDraft = ConfirmEntryDraft(),
    val allItems: List<BudgetItem> = emptyList(),
    val projectId: String = "",
    val source: EntrySource = EntrySource.MANUAL,
    val catalog: TaxonomyCatalog = TaxonomyCatalog(),
)

@HiltViewModel
class ConfirmEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    taxonomyPrefs: TaxonomyPrefs,
) : ViewModel() {

    private val sourceArg: String = savedStateHandle.get<String>("source").orEmpty()
    private val itemIdArg: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val source = when (sourceArg.lowercase()) {
        "voice" -> EntrySource.VOICE
        "image" -> EntrySource.IMAGE
        else -> EntrySource.MANUAL
    }

    private val draftState = MutableStateFlow(buildInitialDraft())

    val uiState = combine(
        projectRepository.observeProjectWithItems(),
        draftState,
        taxonomyPrefs.catalog,
    ) { (project, items), draft, catalog ->
        val resolvedDraft = if (draft.selectedItemId.isEmpty() && itemIdArg.isNotEmpty()) {
            draft.copy(selectedItemId = itemIdArg)
        } else {
            draft
        }
        ConfirmEntryUiState(
            draft = resolvedDraft,
            allItems = items,
            projectId = project.id,
            source = source,
            catalog = catalog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConfirmEntryUiState(source = source),
    )

    private fun buildInitialDraft(): ConfirmEntryDraft = when (source) {
        EntrySource.VOICE -> ConfirmEntryDraft(
            actionType = ConfirmActionType.ADD_PAYMENT,
            itemName = "橱柜定制",
            selectedItemId = "",
            amountYuan = "15000",
            paymentType = PaymentType.DEPOSIT,
            paymentStatus = PaymentStatus.PAID,
            budgetYuan = "20000",
            stage = "主材",
            category = "全屋定制",
            space = "厨房",
            showRecognitionBanner = true,
        )
        EntrySource.IMAGE -> ConfirmEntryDraft(
            actionType = ConfirmActionType.ADD_PAYMENT,
            itemName = "瓷砖采购",
            selectedItemId = "",
            amountYuan = "8600",
            paymentType = PaymentType.FINAL,
            paymentStatus = PaymentStatus.UNPAID,
            budgetYuan = "10000",
            stage = "泥木",
            category = "硬装",
            space = "全屋",
            showRecognitionBanner = true,
        )
        EntrySource.MANUAL -> ConfirmEntryDraft(
            actionType = ConfirmActionType.ADD_PAYMENT,
            selectedItemId = itemIdArg,
            showRecognitionBanner = false,
        )
    }

    fun updateDraft(transform: (ConfirmEntryDraft) -> ConfirmEntryDraft) {
        draftState.value = transform(draftState.value)
    }

    fun save(onSuccess: () -> Unit) {
        val state = uiState.value
        val draft = state.draft
        viewModelScope.launch {
            when (draft.actionType) {
                ConfirmActionType.NEW_ITEM -> {
                    val budgetFen = parseYuanToFen(draft.budgetYuan) ?: return@launch
                    if (draft.itemName.isBlank()) return@launch
                    val itemId = UUID.randomUUID().toString()
                    projectRepository.upsertItem(
                        BudgetItem(
                            id = itemId,
                            projectId = state.projectId,
                            name = draft.itemName.trim(),
                            stage = draft.stage.trim(),
                            category = draft.category.trim(),
                            space = draft.space.trim(),
                            budgetAmount = budgetFen,
                            isNewAddition = true,
                        ),
                    )
                    val amountFen = parseYuanToFen(draft.amountYuan)
                    if (amountFen != null && amountFen > 0L) {
                        projectRepository.upsertPayment(
                            buildPayment(itemId, draft, amountFen),
                        )
                    }
                }
                ConfirmActionType.ADD_PAYMENT -> {
                    val itemId = resolveItemId(state) ?: return@launch
                    val amountFen = parseYuanToFen(draft.amountYuan) ?: return@launch
                    projectRepository.upsertPayment(
                        buildPayment(itemId, draft, amountFen),
                    )
                }
            }
            onSuccess()
        }
    }

    private fun resolveItemId(state: ConfirmEntryUiState): String? {
        val draft = state.draft
        if (draft.selectedItemId.isNotEmpty()) return draft.selectedItemId
        return state.allItems.find { it.name == draft.itemName }?.id
            ?: state.allItems.find { draft.itemName.isNotBlank() && it.name.contains(draft.itemName) }?.id
    }

    private suspend fun buildPayment(
        itemId: String,
        draft: ConfirmEntryDraft,
        amountFen: Long,
    ): Payment {
        val now = System.currentTimeMillis()
        val nickname = userPrefs.userProfile.first().nickname
        return Payment(
            id = UUID.randomUUID().toString(),
            budgetItemId = itemId,
            type = draft.paymentType,
            amount = amountFen,
            status = draft.paymentStatus,
            paidAtEpochMs = if (draft.paymentStatus == PaymentStatus.PAID) now else null,
            note = if (draft.showRecognitionBanner) "识别录入" else "",
            createdBy = nickname,
        )
    }
}
