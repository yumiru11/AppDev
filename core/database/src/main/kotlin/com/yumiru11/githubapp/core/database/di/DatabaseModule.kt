package com.yumiru11.githubapp.core.database.di

import android.content.Context
import androidx.room.Room
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.dao.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room 装配：单例 [AppDatabase] + DAO。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCachedRepositoryDao(db: AppDatabase): CachedRepositoryDao = db.cachedRepositoryDao()

    @Provides
    fun provideCachedReadmeDao(db: AppDatabase): CachedReadmeDao = db.cachedReadmeDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    private const val DATABASE_NAME = "githubapp.db"
}
