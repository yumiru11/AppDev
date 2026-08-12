package com.yumiru11.githubapp.core.database.di

import android.content.Context
import androidx.room.Room
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
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
            ).build()

    @Provides
    fun provideCachedRepositoryDao(db: AppDatabase): CachedRepositoryDao = db.cachedRepositoryDao()

    private const val DATABASE_NAME = "githubapp.db"
}
