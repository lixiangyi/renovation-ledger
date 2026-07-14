package com.renovation.ledger.di

import android.content.Context
import androidx.room.Room
import com.renovation.ledger.data.local.AppDatabase
import com.renovation.ledger.data.local.dao.BudgetItemDao
import com.renovation.ledger.data.local.dao.PaymentDao
import com.renovation.ledger.data.local.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase {
        return Room.databaseBuilder(ctx, AppDatabase::class.java, "renovation.db")
            .addMigrations(
                com.renovation.ledger.data.local.MIGRATION_1_2,
                com.renovation.ledger.data.local.MIGRATION_2_3,
            )
            .build()
    }
    @Provides
    fun projectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun budgetItemDao(db: AppDatabase): BudgetItemDao = db.budgetItemDao()

    @Provides
    fun paymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
}
