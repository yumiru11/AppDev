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
 * v1 schema 验证（MigrationTestHelper + Robolectric）。
 *
 * createDatabase 会用导出的 schema（src/test/assets/schemas）校验
 * Room 生成的 v1 建库语句一致，并实际建库返回可查询的 SupportSQLiteDatabase。
 * 注意 Room 2.8 起 schemaDirectory 须显式传入（旧默认值为空串）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    private val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            // 导出目录名是带点的全限定类名（非斜杠拆分）：assets/schemas/<类名>/<version>.json
            "$SCHEMA_DIRECTORY/${AppDatabase::class.java.name}",
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun createDatabase_v1_buildsExpectedSchema() {
        helper.createDatabase(TEST_DB_NAME, 1).use { db ->
            val tables = queryTableNames(db)
            assertTrue("应包含 cached_repositories 表", tables.contains("cached_repositories"))
            assertEquals(1, tables.size)
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
        const val TEST_DB_NAME = "migration-test-v1"
        const val SCHEMA_DIRECTORY = "schemas"
    }
}
