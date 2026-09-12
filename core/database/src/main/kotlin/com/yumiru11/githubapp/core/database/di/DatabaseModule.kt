package com.yumiru11.githubapp.core.database.di

import android.content.Context
import androidx.room.Room
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.database.dao.IssueDao
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
            ).addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            ).build()

    @Provides
    fun provideCachedRepositoryDao(db: AppDatabase): CachedRepositoryDao = db.cachedRepositoryDao()

    @Provides
    fun provideEtagCacheDao(db: AppDatabase): EtagCacheDao = db.etagCacheDao()

    @Provides
    fun provideCachedReadmeDao(db: AppDatabase): CachedReadmeDao = db.cachedReadmeDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideIssueDao(db: AppDatabase): IssueDao = db.issueDao()

    private const val DATABASE_NAME = "githubapp.db"
}
