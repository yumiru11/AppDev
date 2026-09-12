package com.yumiru11.githubapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.database.dao.IssueDao
import com.yumiru11.githubapp.core.database.dao.SearchHistoryDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.database.entity.CachedRepositoryEntity
import com.yumiru11.githubapp.core.database.entity.EtagCacheEntity
import com.yumiru11.githubapp.core.database.entity.IssueEntity
import com.yumiru11.githubapp.core.database.entity.SearchHistoryEntity

/**
 * 应用本地数据库 v5。
 *
 * v1：仓库响应缓存（ETag 304）
 * v2：+ cached_readme 表（README 双 key 缓存：contentHash + themeVersion）
 * v3：+ search_history 表（T18 搜索历史：query 主键 + 时间戳）
 * v4：+ cached_issues 表（issue #165 / L07 Issue 列表 RemoteMediator 分页缓存）
 * v5：+ etag_cache 表（DATA-1：按账号作用域持久化 ETag + 响应体，跨进程复用 304 缓存）
 */
@Database(
    entities = [
        CachedRepositoryEntity::class,
        CachedReadmeEntity::class,
        SearchHistoryEntity::class,
        IssueEntity::class,
        EtagCacheEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedRepositoryDao(): CachedRepositoryDao

    abstract fun cachedReadmeDao(): CachedReadmeDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun issueDao(): IssueDao

    abstract fun etagCacheDao(): EtagCacheDao

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

        /**
         * v3 → v4：新增 cached_issues 表（issue #165 / L07 Issue 列表分页缓存）。
         *
         * 纯新增表：既有 cached_repositories / cached_readme / search_history 数据原样保留
         * （AppDatabaseMigrationTest.migrate_3to4_* 断言语义）。
         * 列与索引必须与 IssueEntity 的 Room 导出 schema（schemas/4.json）逐字段一致，
         * 否则 MigrationTestHelper.runMigrationsAndValidate 直接失败。
         */
        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `cached_issues` (
                            `owner` TEXT NOT NULL,
                            `repo` TEXT NOT NULL,
                            `filter` TEXT NOT NULL,
                            `issueId` INTEGER NOT NULL,
                            `number` INTEGER NOT NULL,
                            `title` TEXT NOT NULL,
                            `state` TEXT NOT NULL,
                            `authorLogin` TEXT,
                            `authorAvatarUrl` TEXT,
                            `commentCount` INTEGER NOT NULL,
                            `isPullRequest` INTEGER NOT NULL,
                            `updatedAt` TEXT,
                            `htmlUrl` TEXT,
                            `page` INTEGER NOT NULL,
                            `position` INTEGER NOT NULL,
                            `cachedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`owner`, `repo`, `filter`, `issueId`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_cached_issues_owner_repo_filter_page_position` " +
                            "ON `cached_issues` (`owner`, `repo`, `filter`, `page`, `position`)",
                    )
                }
            }

        /**
         * v4 → v5：新增 etag_cache 表（DATA-1 持久化 ETag 缓存，按账号作用域）。
         *
         * 纯新增表：既有四张表数据原样保留。列/主键/索引必须与 EtagCacheEntity 的 Room 导出
         * schema（schemas/5.json）逐字段一致，否则 MigrationTestHelper.runMigrationsAndValidate
         * 直接失败。
         */
        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `etag_cache` (
                            `scope` TEXT NOT NULL,
                            `method` TEXT NOT NULL,
                            `url` TEXT NOT NULL,
                            `etag` TEXT NOT NULL,
                            `contentType` TEXT,
                            `body` TEXT NOT NULL,
                            `bodyBytes` INTEGER NOT NULL,
                            `storedAt` INTEGER NOT NULL,
                            `lastAccessedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`scope`, `method`, `url`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_etag_cache_lastAccessedAt` " +
                            "ON `etag_cache` (`lastAccessedAt`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_etag_cache_storedAt` " +
                            "ON `etag_cache` (`storedAt`)",
                    )
                }
            }
    }
}
