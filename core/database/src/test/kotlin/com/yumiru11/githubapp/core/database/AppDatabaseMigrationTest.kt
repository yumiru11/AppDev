package com.yumiru11.githubapp.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1~v4 schema 验证 + v1→v2、v2→v3、v3→v4 迁移测试（MigrationTestHelper + Robolectric）。
 *
 * v4（issue #165 / L07）新增 cached_issues 表：必须验证既有 cached_repositories /
 * cached_readme / search_history 数据在升级后原样保留（禁 destructive migration）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    private val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            "$SCHEMA_DIRECTORY/${AppDatabase::class.java.name}",
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun createDatabase_v1_buildsExpectedSchema() {
        helper.createDatabase(TEST_DB_NAME_V1, 1).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_repositories 表", tables.contains("cached_repositories"))
            assertEquals(1, tables.size)
        }
    }

    @Test
    fun createDatabase_v2_buildsExpectedSchema() {
        helper.createDatabase(TEST_DB_NAME_V2_SCHEMA, 2).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_repositories 表", tables.contains("cached_repositories"))
            assertTrue("应包含 cached_readme 表", tables.contains("cached_readme"))
            assertEquals(2, tables.size)
        }
    }

    @Test
    fun migrate_1to2_addsCachedReadmeTable() {
        // 先创建 v1 数据库
        helper.createDatabase(TEST_DB_NAME_V2, 1).use { db ->
            // 插入一条 v1 数据，验证迁移后数据保留
            db.execSQL(
                """
                INSERT INTO cached_repositories (owner, name, etag, payload, updatedAt)
                VALUES ('octocat', 'Hello-World', 'W/"abc"', '{"id":1}', 1700000000000)
                """.trimIndent(),
            )
        }

        // 迁移到 v2
        helper.runMigrationsAndValidate(TEST_DB_NAME_V2, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_repositories 表", tables.contains("cached_repositories"))
            assertTrue("应包含 cached_readme 表", tables.contains("cached_readme"))
            assertEquals(2, tables.size)

            // 验证 v1 数据保留
            val cursor = db.query("SELECT owner, name FROM cached_repositories", emptyArray())
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("octocat", it.getString(0))
                assertEquals("Hello-World", it.getString(1))
            }

            // 验证 cached_readme 表结构：插入并查询
            db.execSQL(
                """
                INSERT INTO cached_readme (owner, repo, contentHash, themeVersion, html, updatedAt)
                VALUES ('octocat', 'Hello-World', 'abc123', 'v1', '<h1>Hi</h1>', 1700000000000)
                """.trimIndent(),
            )
            val readmeCursor = db.query("SELECT owner, repo, contentHash FROM cached_readme", emptyArray())
            readmeCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("octocat", it.getString(0))
                assertEquals("Hello-World", it.getString(1))
                assertEquals("abc123", it.getString(2))
            }
        }
    }

    @Test
    fun migrate_2to3_addsSearchHistoryTable() {
        // 先创建 v2 数据库
        helper.createDatabase(TEST_DB_NAME_V3, 2).use { db ->
            // 插入一条 v2 数据，验证迁移后数据保留
            db.execSQL(
                """
                INSERT INTO cached_readme (owner, repo, contentHash, themeVersion, html, updatedAt)
                VALUES ('octocat', 'Hello-World', 'abc123', 'v1', '<h1>Hi</h1>', 1700000000000)
                """.trimIndent(),
            )
        }

        // 迁移到 v3
        helper.runMigrationsAndValidate(TEST_DB_NAME_V3, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_repositories 表", tables.contains("cached_repositories"))
            assertTrue("应包含 cached_readme 表", tables.contains("cached_readme"))
            assertTrue("应包含 search_history 表", tables.contains("search_history"))
            assertEquals(3, tables.size)

            // 验证 v2 数据保留
            val cursor = db.query("SELECT owner, repo FROM cached_readme", emptyArray())
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("octocat", it.getString(0))
                assertEquals("Hello-World", it.getString(1))
            }

            // 验证 search_history 表结构：插入并查询
            db.execSQL(
                """
                INSERT INTO search_history (query, updatedAt)
                VALUES ('kotlin', 1700000000000)
                """.trimIndent(),
            )
            val historyCursor = db.query("SELECT query, updatedAt FROM search_history", emptyArray())
            historyCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("kotlin", it.getString(0))
                assertEquals(1700000000000L, it.getLong(1))
            }
        }
    }

    @Test
    fun createDatabase_v4_buildsExpectedSchema() {
        helper.createDatabase(TEST_DB_NAME_V4_SCHEMA, 4).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_repositories 表", tables.contains("cached_repositories"))
            assertTrue("应包含 cached_readme 表", tables.contains("cached_readme"))
            assertTrue("应包含 search_history 表", tables.contains("search_history"))
            assertTrue("应包含 cached_issues 表", tables.contains("cached_issues"))
            assertEquals(4, tables.size)
        }
    }

    @Test
    fun migrate_3to4_addsCachedIssuesTableAndKeepsReadmeCache() {
        // 先创建 v3 数据库并写入 README/仓库缓存（升级不得丢数据）
        helper.createDatabase(TEST_DB_NAME_V4, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO cached_repositories (owner, name, etag, payload, updatedAt)
                VALUES ('octocat', 'Hello-World', 'W/"abc"', '{"id":1}', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO cached_readme (owner, repo, contentHash, themeVersion, html, updatedAt)
                VALUES ('octocat', 'Hello-World', 'abc123', 'v1', '<h1>Hi</h1>', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO search_history (query, updatedAt) VALUES ('kotlin', 1700000000000)")
        }

        helper.runMigrationsAndValidate(TEST_DB_NAME_V4, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_issues 表", tables.contains("cached_issues"))
            assertEquals(4, tables.size)

            // v3 数据全部保留
            val repoCursor = db.query("SELECT owner, name, etag FROM cached_repositories", emptyArray())
            repoCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("octocat", it.getString(0))
                assertEquals("Hello-World", it.getString(1))
                assertEquals("W/\"abc\"", it.getString(2))
            }
            val readmeCursor = db.query("SELECT owner, repo, html FROM cached_readme", emptyArray())
            readmeCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("octocat", it.getString(0))
                assertEquals("Hello-World", it.getString(1))
                assertEquals("<h1>Hi</h1>", it.getString(2))
            }
            val historyCursor = db.query("SELECT query FROM search_history", emptyArray())
            historyCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("kotlin", it.getString(0))
            }

            // 新表可写可读（列/主键与 IssueEntity 一致）
            db.execSQL(
                """
                INSERT INTO cached_issues
                    (owner, repo, filter, issueId, number, title, state, authorLogin, authorAvatarUrl,
                     commentCount, isPullRequest, updatedAt, htmlUrl, page, position, cachedAt)
                VALUES ('octocat', 'Hello-World', 'open', 1347, 42, 'Bug report', 'open', 'octocat',
                        'https://avatars.githubusercontent.com/u/1', 3, 0, '2026-09-06T00:00:00Z',
                        'https://github.com/octocat/Hello-World/issues/42', 1, 0, 1700000000000)
                """.trimIndent(),
            )
            val issueCursor = db.query("SELECT issueId, title, page, position FROM cached_issues", emptyArray())
            issueCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals(1347L, it.getLong(0))
                assertEquals("Bug report", it.getString(1))
                assertEquals(1, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
        }
    }

    @Test
    fun migrate_1to4_fullChain_keepsRepositoryCacheAndAddsAllTables() {
        helper.createDatabase(TEST_DB_NAME_V4_FULL, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO cached_repositories (owner, name, etag, payload, updatedAt)
                VALUES ('octocat', 'Hello-World', 'W/"v1"', '{"id":1}', 1700000000000)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                TEST_DB_NAME_V4_FULL,
                4,
                true,
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            ).use { db ->
                val tables = queryTableNames(db)
                assertEquals(4, tables.size)
                val cursor = db.query("SELECT owner, name, etag FROM cached_repositories", emptyArray())
                cursor.use {
                    assertTrue(it.moveToFirst())
                    assertEquals("octocat", it.getString(0))
                    assertEquals("W/\"v1\"", it.getString(2))
                }
            }
    }

    @Test
    fun createDatabase_v5_buildsExpectedSchema() {
        helper.createDatabase(TEST_DB_NAME_V5_SCHEMA, 5).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 etag_cache 表", tables.contains("etag_cache"))
            assertEquals(5, tables.size)
        }
    }

    @Test
    fun migrate_4to5_addsEtagCacheTableAndKeepsExistingCaches() {
        helper.createDatabase(TEST_DB_NAME_V5, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO cached_repositories (owner, name, etag, payload, updatedAt)
                VALUES ('octocat', 'Hello-World', 'W/"abc"', '{"id":1}', 1700000000000)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB_NAME_V5, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 etag_cache 表", tables.contains("etag_cache"))
            assertEquals(5, tables.size)

            val cursor = db.query("SELECT owner, name, etag FROM cached_repositories", emptyArray())
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("octocat", it.getString(0))
                assertEquals("W/\"abc\"", it.getString(2))
            }

            db.execSQL(
                """
                INSERT INTO etag_cache
                    (scope, method, url, etag, contentType, body, bodyBytes, storedAt, lastAccessedAt)
                VALUES ('a', 'GET', 'https://api.github.com/user', 'W/"v1"', 'application/json', '{}', 2, 1, 1)
                """.trimIndent(),
            )
            val etagCursor = db.query("SELECT scope, method, url, etag FROM etag_cache", emptyArray())
            etagCursor.use {
                assertTrue(it.moveToFirst())
                assertEquals("a", it.getString(0))
                assertEquals("GET", it.getString(1))
                assertEquals("https://api.github.com/user", it.getString(2))
                assertEquals("W/\"v1\"", it.getString(3))
            }
        }
    }

    @Test
    fun migrate_1to5_fullChain_keepsRepositoryCacheAndAddsAllTables() {
        helper.createDatabase(TEST_DB_NAME_V5_FULL, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO cached_repositories (owner, name, etag, payload, updatedAt)
                VALUES ('octocat', 'Hello-World', 'W/"v1"', '{"id":1}', 1700000000000)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                TEST_DB_NAME_V5_FULL,
                5,
                true,
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            ).use { db ->
                val tables = queryTableNames(db)
                assertEquals(5, tables.size)
                val cursor = db.query("SELECT owner, name, etag FROM cached_repositories", emptyArray())
                cursor.use {
                    assertTrue(it.moveToFirst())
                    assertEquals("W/\"v1\"", it.getString(2))
                }
            }
    }

    private fun queryTableNames(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        val sql =
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'room%' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'android_metadata'"
        db
            .query(sql, emptyArray())
            .use { cursor ->
                while (cursor.moveToNext()) {
                    names += cursor.getString(0)
                }
            }
        return names
    }

    private companion object {
        const val TEST_DB_NAME_V1 = "migration-test-v1"
        const val TEST_DB_NAME_V2 = "migration-test-v2"
        const val TEST_DB_NAME_V2_SCHEMA = "migration-test-v2-schema"
        const val TEST_DB_NAME_V3 = "migration-test-v3"
        const val TEST_DB_NAME_V4 = "migration-test-v4"
        const val TEST_DB_NAME_V4_SCHEMA = "migration-test-v4-schema"
        const val TEST_DB_NAME_V4_FULL = "migration-test-v4-full"
        const val TEST_DB_NAME_V5 = "migration-test-v5"
        const val TEST_DB_NAME_V5_SCHEMA = "migration-test-v5-schema"
        const val TEST_DB_NAME_V5_FULL = "migration-test-v5-full"
        const val SCHEMA_DIRECTORY = "schemas"
    }
}
