package com.yumiru11.githubapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.dao.SearchHistoryDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.database.entity.CachedRepositoryEntity
import com.yumiru11.githubapp.core.database.entity.SearchHistoryEntity

/**
 * 应用本地数据库 v3。
 *
 * v1：仓库响应缓存（ETag 304）
 * v2：+ cached_readme 表（README 双 key 缓存：contentHash + themeVersion）
 * v3：+ search_history 表（T18 搜索历史：query 主键 + 时间戳）
 */
@Database(
    entities = [CachedRepositoryEntity::class, CachedReadmeEntity::class, SearchHistoryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedRepositoryDao(): CachedRepositoryDao

    abstract fun cachedReadmeDao(): CachedReadmeDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        /**
         * v1 → v2：新增 cached_readme 表。
         *
         * CREATE TABLE IF NOT EXISTS 保证幂等，Migration 2 在升级后自动触发。
         */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `cached_readme` (
                            `owner` TEXT NOT NULL,
                            `repo` TEXT NOT NULL,
                            `contentHash` TEXT NOT NULL,
                            `themeVersion` TEXT NOT NULL,
                            `html` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`owner`, `repo`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        /**
         * v2 → v3：新增 search_history 表（T18 搜索历史）。
         */
        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `search_history` (
                            `query` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`query`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
