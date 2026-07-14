package com.renovation.ledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.renovation.ledger.data.local.dao.BudgetItemDao
import com.renovation.ledger.data.local.dao.PaymentDao
import com.renovation.ledger.data.local.dao.ProjectDao
import com.renovation.ledger.data.local.entity.BudgetItemEntity
import com.renovation.ledger.data.local.entity.PaymentEntity
import com.renovation.ledger.data.local.entity.ProjectEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budget_items ADD COLUMN recordedDate TEXT")
        db.execSQL("ALTER TABLE budget_items ADD COLUMN remark TEXT NOT NULL DEFAULT ''")
    }
}

/** 历史数据：分类为空时把 stage（旧「所属类别」）回填到 category。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE budget_items
            SET category = stage
            WHERE (category IS NULL OR TRIM(category) = '')
              AND stage IS NOT NULL
              AND TRIM(stage) != ''
            """.trimIndent(),
        )
    }
}

@Database(
    entities = [ProjectEntity::class, BudgetItemEntity::class, PaymentEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun budgetItemDao(): BudgetItemDao
    abstract fun paymentDao(): PaymentDao
}
