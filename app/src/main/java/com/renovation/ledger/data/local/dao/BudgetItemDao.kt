package com.renovation.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.renovation.ledger.data.local.entity.BudgetItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetItemDao {
    @Query("SELECT * FROM budget_items WHERE projectId = :projectId")
    fun observeByProject(projectId: String): Flow<List<BudgetItemEntity>>

    @Query("SELECT * FROM budget_items WHERE id = :id")
    fun observeById(id: String): Flow<BudgetItemEntity?>

    @Query("SELECT COUNT(*) FROM budget_items")
    suspend fun countAll(): Long

    /**
     * 用 [Upsert] 而非 OnConflictStrategy.REPLACE：
     * REPLACE 会先 DELETE 再 INSERT，触发 payments 的 CASCADE，误删付款记录。
     */
    @Upsert
    suspend fun upsert(item: BudgetItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<BudgetItemEntity>)

    @Delete
    suspend fun delete(item: BudgetItemEntity)

    @Query("DELETE FROM budget_items")
    suspend fun deleteAll()
}
