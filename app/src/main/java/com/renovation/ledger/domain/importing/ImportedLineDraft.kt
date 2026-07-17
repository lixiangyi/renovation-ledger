package com.renovation.ledger.domain.importing

import com.renovation.ledger.domain.model.PaymentStatus
import com.renovation.ledger.domain.model.PaymentType

data class ImportedPaymentDraft(
    val type: PaymentType,
    val amountCents: Long,
    val status: PaymentStatus,
    val paidAtEpochMs: Long? = null,
    val note: String = "",
    val createdBy: String = "",
)

data class ImportedLineDraft(
    val name: String,
    /** 确认页合计 / 去重：优先预算 */
    val amountCents: Long,
    val recordedDate: String? = null,
    val stage: String,
    val category: String = "",
    val space: String = "",
    val merchant: String = "",
    val remark: String = "",
    val budgetCents: Long = amountCents,
    val contractCents: Long? = amountCents,
    val payments: List<ImportedPaymentDraft> = emptyList(),
    val sourceImageIndex: Int? = null,
    val isDuplicate: Boolean = false,
    val selected: Boolean = true,
)
