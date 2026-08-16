package com.yumiru11.githubapp.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.dao.SearchHistoryDao
import com.yumiru11.githubapp.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SearchHistoryDao 读写测试（Robolectric + inMemoryDatabaseBuilder，T18 搜索历史）。
 *
 * 覆盖：upsert 插入/同词去重、getAll 时间倒序、getRecent 数量限制、clear 清空。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchHistoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SearchHistoryDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
        dao = db.searchHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_insertsEntity_thenQueriesBack() =
        runTest {
            dao.upsert(SearchHistoryEntity(query = "kotlin", updatedAt = 1_000L))

            val all = dao.getAll()

            assertEquals(listOf("kotlin"), all.map { it.query })
        }

    @Test
    fun upsert_sameQuery_updatesTimestampWithoutDuplicating() =
        runTest {
            dao.upsert(SearchHistoryEntity(query = "kotlin", updatedAt = 1_000L))
            dao.upsert(SearchHistoryEntity(query = "kotlin", updatedAt = 2_000L))

            val all = dao.getAll()

            assertEquals(1, all.size)
            assertEquals(2_000L, all.single().updatedAt)
        }

    @Test
    fun getAll_returnsEntriesOrderedByUpdatedAtDescending() =
        runTest {
            dao.upsert(SearchHistoryEntity(query = "old", updatedAt = 1_000L))
            dao.upsert(SearchHistoryEntity(query = "new", updatedAt = 3_000L))
            dao.upsert(SearchHistoryEntity(query = "mid", updatedAt = 2_000L))

            assertEquals(listOf("new", "mid", "old"), dao.getAll().map { it.query })
        }

    @Test
    fun getRecent_limitsCountAndKeepsNewest() =
        runTest {
            dao.upsert(SearchHistoryEntity(query = "a", updatedAt = 1_000L))
            dao.upsert(SearchHistoryEntity(query = "b", updatedAt = 2_000L))
            dao.upsert(SearchHistoryEntity(query = "c", updatedAt = 3_000L))

            assertEquals(listOf("c", "b"), dao.getRecent(limit = 2))
        }

    @Test
    fun getRecent_emptyTable_returnsEmptyList() =
        runTest {
            assertEquals(emptyList<String>(), dao.getRecent(limit = 10))
        }

    @Test
    fun clear_removesAllEntries() =
        runTest {
            dao.upsert(SearchHistoryEntity(query = "a", updatedAt = 1_000L))
            dao.upsert(SearchHistoryEntity(query = "b", updatedAt = 2_000L))

            dao.clear()

            assertEquals(emptyList<String>(), dao.getRecent(limit = 10))
        }

    @Test
    fun clear_emptyTable_isNoOp() =
        runTest {
            dao.clear()

            assertEquals(emptyList<String>(), dao.getAll().map { it.query })
        }
}
