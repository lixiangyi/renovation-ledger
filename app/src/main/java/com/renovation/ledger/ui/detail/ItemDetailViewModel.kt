package com.renovation.ledger.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renovation.ledger.data.prefs.TaxonomyPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.domain.model.BudgetItem
import com.renovation.ledger.domain.model.ItemStatus
import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType
import com.renovation.ledger.domain.model.deriveStatus
import com.renovation.ledger.domain.taxonomy.TaxonomyCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val item: BudgetItem? = null,
    val status: ItemStatus = ItemStatus.TO_BUY,
    val paidSum: Long = 0L,
    val unpaidSum: Long = 0L,
    val isOverBudget: Boolean = false,
    val catalog: TaxonomyCatalog = TaxonomyCatalog(),
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    taxonomyPrefs: TaxonomyPrefs,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("id").orEmpty()

    val uiState = combine(
        projectRepository.observeItem(itemId),
        taxonomyPrefs.catalog,
    ) { item, catalog ->
        if (item == null) {
            ItemDetailUiState(catalog = catalog)
        } else {
            val paidSum = item.payments
                .filter { it.status == PaymentStatus.PAID }
                .sumOf { it.amount }
            val unpaidSum = item.payments
                .filter { it.status == PaymentStatus.UNPAID }
                .sumOf { it.amount }
            val effective = item.contractAmount ?: item.budgetAmount
            ItemDetailUiState(
                item = item,
                status = item.deriveStatus(),
                paidSum = paidSum,
                unpaidSum = unpaidSum,
                isOverBudget = effective > item.budgetAmount,
                catalog = catalog,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItemDetailUiState(),
    )

    fun settleItem() {
        val item = uiState.value.item ?: return
        viewModelScope.launch {
            projectRepository.settleItem(item)
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            projectRepository.deleteItem(itemId)
            onDeleted()
        }
    }

    fun updateItem(
        name: String,
        budgetYuan: String,
        contractYuan: String,
        recordedDate: String,
        remark: String,
        stage: String,
        category: String,
        space: String,
    ) {
        val item = uiState.value.item ?: return
        val budgetFen = parseYuanToFen(budgetYuan) ?: return
        val contractFen = contractYuan.trim().takeIf { it.isNotEmpty() }?.let { parseYuanToFen(it) }
        viewModelScope.launch {
            projectRepository.upsertItem(
                item.copy(
                    name = name.trim(),
                    budgetAmount = budgetFen,
                    contractAmount = contractFen,
                    recordedDate = recordedDate.trim().ifBlank { null },
                    remark = remark.trim(),
                    stage = stage.trim(),
                    category = category.trim(),
                    space = space.trim(),
                ),
            )
        }
    }

    fun updatePayment(
        paymentId: String,
        type: PaymentType,
        amountYuan: String,
        status: PaymentStatus,
        note: String,
    ) {
        val item = uiState.value.item ?: return
        val payment = item.payments.find { it.id == paymentId } ?: return
        val amountFen = parseYuanToFen(amountYuan) ?: return
        val paidAt = when {
            status == PaymentStatus.PAID -> payment.paidAtEpochMs ?: System.currentTimeMillis()
            else -> null
        }
        viewModelScope.launch {
            projectRepository.upsertPayment(
                payment.copy(
                    type = type,
                    amount = amountFen,
                    status = status,
                    paidAtEpochMs = paidAt,
                    note = note.trim(),
                ),
            )
        }
    }

    fun deletePayment(paymentId: String) {
        val payment = uiState.value.item?.payments?.find { it.id == paymentId } ?: return
        viewModelScope.launch {
            projectRepository.deletePayment(payment)
        }
    }
}

internal fun parseYuanToFen(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return try {
        (trimmed.toDouble() * 100).toLong()
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun fenToYuanString(fen: Long): String = (fen / 100.0).let { value ->
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.2f", value)
    }
}

internal fun fenToYuanStringOrEmpty(fen: Long?): String =
    fen?.let { fenToYuanString(it) }.orEmpty()
