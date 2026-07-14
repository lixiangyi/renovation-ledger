package com.renovation.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.renovation.ledger.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY name ASC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    /** 兼容旧调用：取第一本（按 name 排序）。 */
    @Query("SELECT * FROM projects ORDER BY name ASC LIMIT 1")
    fun observeDefault(): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects ORDER BY name ASC LIMIT 1")
    suspend fun getDefault(): ProjectEntity?

    /** Upsert：避免 REPLACE 删除项目行而 CASCADE 清空全部预算项/付款。 */
    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
