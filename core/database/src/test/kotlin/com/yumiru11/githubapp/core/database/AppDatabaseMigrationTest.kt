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
 * v1/v2 schema 验证 + v1→v2 迁移测试（MigrationTestHelper + Robolectric）。
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
        const val SCHEMA_DIRECTORY = "schemas"
    }
}
