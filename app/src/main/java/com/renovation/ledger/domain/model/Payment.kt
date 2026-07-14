package com.renovation.ledger.domain.model

data class Payment(
    val id: String,
    val budgetItemId: String,
    val type: PaymentType,
    val amount: Long,
    val status: PaymentStatus,
    val paidAtEpochMs: Long? = null,
    val note: String = "",
    val receiptUri: String? = null,
    val createdBy: String = "",
)
