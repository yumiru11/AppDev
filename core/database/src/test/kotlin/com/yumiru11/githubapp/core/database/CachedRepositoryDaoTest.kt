package com.yumiru11.githubapp.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.entity.CachedRepositoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CachedRepositoryDao 读写测试（Robolectric + inMemoryDatabaseBuilder）。
 *
 * 覆盖：upsert 插入/覆盖、按 owner/name 查询、列表按 updatedAt 倒序、删除、清空。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedRepositoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: CachedRepositoryDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.cachedRepositoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_insertsEntity_thenQueriesBack() =
        runTest {
            val entity = cachedRepository(etag = "W/\"abc\"", payload = "{\"id\":1}")

            dao.upsert(entity)
            val loaded = dao.getByOwnerAndName("octocat", "Hello-World")

            assertEquals(entity, loaded)
        }

    @Test
    fun upsert_samePrimaryKey_overwritesPayload() =
        runTest {
            dao.upsert(cachedRepository(etag = "W/\"old\"", payload = "old-payload"))
            dao.upsert(cachedRepository(etag = "W/\"new\"", payload = "new-payload"))

            val loaded = dao.getByOwnerAndName("octocat", "Hello-World")

            assertEquals("W/\"new\"", loaded?.etag)
            assertEquals("new-payload", loaded?.payload)
            assertEquals(1, dao.getAll().size)
        }

    @Test
    fun getAll_returnsEntriesOrderedByUpdatedAtDescending() =
        runTest {
            dao.upsert(cachedRepository(owner = "a", name = "older", updatedAt = 1_000L))
            dao.upsert(cachedRepository(owner = "b", name = "newer", updatedAt = 2_000L))

            val all = dao.getAll()

            assertEquals(listOf("b/newer", "a/older"), all.map { "${it.owner}/${it.name}" })
        }

    @Test
    fun delete_removesOnlyMatchingEntry() =
        runTest {
            dao.upsert(cachedRepository(owner = "octocat", name = "Hello-World"))
            dao.upsert(cachedRepository(owner = "octocat", name = "Other-Repo"))

            dao.delete("octocat", "Hello-World")

            assertNull(dao.getByOwnerAndName("octocat", "Hello-World"))
            assertEquals("Other-Repo", dao.getByOwnerAndName("octocat", "Other-Repo")?.name)
        }

    @Test
    fun clear_removesAllEntries() =
        runTest {
            dao.upsert(cachedRepository(owner = "octocat", name = "Hello-World"))
            dao.upsert(cachedRepository(owner = "octocat", name = "Other-Repo"))

            dao.clear()

            assertEquals(0, dao.getAll().size)
        }

    @Test
    fun getByOwnerAndName_missingKey_returnsNull() =
        runTest {
            dao.upsert(cachedRepository(owner = "octocat", name = "Hello-World"))

            val missing = dao.getByOwnerAndName("nobody", "No-Such-Repo")
            val wrongOwner = dao.getByOwnerAndName("other", "Hello-World")

            assertNull(missing)
            assertNull(wrongOwner)
        }

    @Test
    fun delete_missingKey_keepsExistingRowsUntouched() =
        runTest {
            dao.upsert(cachedRepository(owner = "octocat", name = "Hello-World", payload = "{\"kept\":true}"))

            dao.delete("nobody", "No-Such-Repo")

            assertEquals(1, dao.getAll().size)
            assertEquals("{\"kept\":true}", dao.getByOwnerAndName("octocat", "Hello-World")?.payload)
        }

    @Test
    fun upsert_nullEtag_roundTripsAsNull() =
        runTest {
            // 响应未带校验头（etag 为空）→ 存库后仍为 null，payload 等非空字段保留
            dao.upsert(cachedRepository(etag = null, payload = "{\"id\":1}"))

            val loaded = dao.getByOwnerAndName("octocat", "Hello-World")

            assertNull(loaded?.etag)
            assertEquals("{\"id\":1}", loaded?.payload)
            assertEquals(1_700_000_000_000L, loaded?.updatedAt)
        }

    private fun cachedRepository(
        owner: String = "octocat",
        name: String = "Hello-World",
        etag: String? = "W/\"etag\"",
        payload: String = "{\"name\":\"Hello-World\"}",
        updatedAt: Long = 1_700_000_000_000L,
    ): CachedRepositoryEntity =
        CachedRepositoryEntity(
            owner = owner,
            name = name,
            etag = etag,
            payload = payload,
            updatedAt = updatedAt,
        )
}
