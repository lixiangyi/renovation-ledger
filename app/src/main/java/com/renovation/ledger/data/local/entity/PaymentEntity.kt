package com.renovation.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = BudgetItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["budgetItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("budgetItemId")],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val budgetItemId: String,
    val type: String,
    val amount: Long,
    val status: String,
    val paidAtEpochMs: Long?,
    val note: String,
    val receiptUri: String?,
    val createdBy: String,
)
