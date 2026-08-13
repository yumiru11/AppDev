package com.yumiru11.githubapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.database.entity.CachedRepositoryEntity

/**
 * 应用本地数据库 v2。
 *
 * v1：仓库响应缓存（ETag 304）
 * v2：+ cached_readme 表（README 双 key 缓存：contentHash + themeVersion）
 */
@Database(
    entities = [CachedRepositoryEntity::class, CachedReadmeEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedRepositoryDao(): CachedRepositoryDao

    abstract fun cachedReadmeDao(): CachedReadmeDao

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
    }
}
