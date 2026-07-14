package com.renovation.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budget_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class BudgetItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val stage: String,
    val category: String,
    val space: String,
    val budgetAmount: Long,
    val contractAmount: Long?,
    val merchant: String,
    val recordedDate: String? = null,
    val remark: String = "",
    val isNewAddition: Boolean,
)
