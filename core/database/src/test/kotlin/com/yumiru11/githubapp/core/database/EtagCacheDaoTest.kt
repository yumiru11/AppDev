package com.yumiru11.githubapp.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.database.entity.EtagCacheEntity
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
 * EtagCacheDao 读写/驱逐查询测试（Robolectric + inMemoryDatabaseBuilder）。
 *
 * 覆盖复合主键 (scope, method, url)、touch、clear、deleteOtherScopes、deleteExpired、
 * count/totalBytes 聚合、LRU 删除、按作用域查询排序。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EtagCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: EtagCacheDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.etagCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_thenGet_roundTripsEntity() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/user"))

            val loaded = dao.get("a", "GET", "https://api.github.com/user")

            assertEquals(entity(scope = "a", url = "https://api.github.com/user"), loaded)
        }

    @Test
    fun get_missingKey_returnsNull() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/user"))

            assertNull(dao.get("b", "GET", "https://api.github.com/user"))
            assertNull(dao.get("a", "POST", "https://api.github.com/user"))
            assertNull(dao.get("a", "GET", "https://api.github.com/other"))
        }

    @Test
    fun upsert_samePrimaryKey_overwrites() =
        runTest {
            dao.upsert(entity(scope = "a", etag = "old", body = "old-body"))
            dao.upsert(entity(scope = "a", etag = "new", body = "new-body"))

            val loaded = dao.get("a", "GET", DEFAULT_URL)
            assertEquals("new", loaded?.etag)
            assertEquals("new-body", loaded?.body)
            assertEquals(1, dao.count())
        }

    @Test
    fun touch_updatesLastAccessedAtOnly() =
        runTest {
            dao.upsert(entity(scope = "a", storedAt = 10, lastAccessedAt = 10))

            dao.touch("a", "GET", DEFAULT_URL, at = 99)

            val loaded = dao.get("a", "GET", DEFAULT_URL)
            assertEquals(99L, loaded?.lastAccessedAt)
            assertEquals(10L, loaded?.storedAt)
        }

    @Test
    fun delete_removesOnlyTargetKey() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/one"))
            dao.upsert(entity(scope = "a", url = "https://api.github.com/two"))

            dao.delete("a", "GET", "https://api.github.com/one")

            assertNull(dao.get("a", "GET", "https://api.github.com/one"))
            assertEquals(1, dao.count())
        }

    @Test
    fun clear_removesAllScopes() =
        runTest {
            dao.upsert(entity(scope = "a"))
            dao.upsert(entity(scope = "b", url = "https://api.github.com/two"))

            dao.clear()

            assertEquals(0, dao.count())
        }

    @Test
    fun deleteOtherScopes_keepsOnlyGivenScope() =
        runTest {
            dao.upsert(entity(scope = "a"))
            dao.upsert(entity(scope = "b", url = "https://api.github.com/two"))

            dao.deleteOtherScopes("a")

            assertEquals(1, dao.count())
            assertEquals(1, dao.getByScope("a").size)
            assertEquals(0, dao.getByScope("b").size)
        }

    @Test
    fun deleteExpired_removesRowsStoredBeforeCutoff() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/old", storedAt = 100))
            dao.upsert(entity(scope = "a", url = "https://api.github.com/new", storedAt = 900))

            dao.deleteExpired(cutoff = 500)

            assertNull(dao.get("a", "GET", "https://api.github.com/old"))
            assertEquals(1, dao.count())
        }

    @Test
    fun countAndTotalBytes_aggregateAcrossRows() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/one", bodyBytes = 30))
            dao.upsert(entity(scope = "a", url = "https://api.github.com/two", bodyBytes = 12))

            assertEquals(2, dao.count())
            assertEquals(42L, dao.totalBytes())
        }

    @Test
    fun deleteLeastRecentlyUsed_removesOldestByAccess() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/one", lastAccessedAt = 10))
            dao.upsert(entity(scope = "a", url = "https://api.github.com/two", lastAccessedAt = 20))
            dao.upsert(entity(scope = "a", url = "https://api.github.com/three", lastAccessedAt = 30))

            dao.deleteLeastRecentlyUsed(2)

            assertEquals(1, dao.count())
            assertEquals("https://api.github.com/three", dao.getByScope("a").single().url)
        }

    @Test
    fun getByScope_ordersByLastAccessedDescending() =
        runTest {
            dao.upsert(entity(scope = "a", url = "https://api.github.com/older", lastAccessedAt = 10))
            dao.upsert(entity(scope = "a", url = "https://api.github.com/newer", lastAccessedAt = 20))
            dao.upsert(entity(scope = "b", url = "https://api.github.com/other", lastAccessedAt = 30))

            val urls = dao.getByScope("a").map { it.url }

            assertEquals(listOf("https://api.github.com/newer", "https://api.github.com/older"), urls)
        }

    private fun entity(
        scope: String = "a",
        method: String = "GET",
        url: String = DEFAULT_URL,
        etag: String = "W/\"etag\"",
        body: String = "{\"id\":1}",
        bodyBytes: Long = 8,
        storedAt: Long = 1_700_000_000_000L,
        lastAccessedAt: Long = storedAt,
    ): EtagCacheEntity =
        EtagCacheEntity(
            scope = scope,
            method = method,
            url = url,
            etag = etag,
            contentType = "application/json",
            body = body,
            bodyBytes = bodyBytes,
            storedAt = storedAt,
            lastAccessedAt = lastAccessedAt,
        )

    private companion object {
        const val DEFAULT_URL = "https://api.github.com/repos/octocat/Hello-World"
    }
}
