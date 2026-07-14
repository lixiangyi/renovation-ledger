package com.renovation.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.renovation.ledger.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE budgetItemId = :itemId")
    fun observeByItem(itemId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE budgetItemId IN (:itemIds)")
    fun observeByItems(itemIds: List<String>): Flow<List<PaymentEntity>>

    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
