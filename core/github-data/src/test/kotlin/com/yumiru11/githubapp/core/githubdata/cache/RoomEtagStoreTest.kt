package com.yumiru11.githubapp.core.githubdata.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.githubrest.http.EtagEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RoomEtagStore 行为测试（Robolectric + in-memory Room）。
 *
 * 覆盖：读写往返、账号作用域隔离、切账号清旧作用域、登出清空、TTL 过期、单条超限拒收、
 * 条数上界 LRU 驱逐、字节上界驱逐、访问刷新 LRU。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomEtagStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: EtagCacheDao
    private var now = 1_000_000L
    private lateinit var store: RoomEtagStore

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).build()
        dao = db.etagCacheDao()
        store = RoomEtagStore(dao, clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun get_missingKey_returnsNull() {
        assertNull(store.get(SCOPE_A, "GET", url(1)))
    }

    @Test
    fun put_thenGet_roundTripsEntry() {
        store.put(SCOPE_A, "GET", url(1), entry(etag = "W/\"e1\"", body = "{\"id\":1}"))

        val loaded = store.get(SCOPE_A, "GET", url(1))

        assertEquals(EtagEntry("W/\"e1\"", "application/json", "{\"id\":1}"), loaded)
    }

    @Test
    fun put_nullContentType_roundTripsAsNull() {
        store.put(SCOPE_A, "GET", url(1), entry(contentType = null))

        assertNull(store.get(SCOPE_A, "GET", url(1))?.contentType)
    }

    @Test
    fun get_differentScope_returnsNull() {
        store.put(SCOPE_A, "GET", url(1), entry(body = "{\"secret\":\"account-a\"}"))

        assertNull(store.get(SCOPE_B, "GET", url(1)))
        assertEquals("{\"secret\":\"account-a\"}", store.get(SCOPE_A, "GET", url(1))?.body)
    }

    @Test
    fun get_sameUrlDifferentMethod_returnsNull() {
        store.put(SCOPE_A, "GET", url(1), entry())

        assertNull(store.get(SCOPE_A, "POST", url(1)))
    }

    @Test
    fun put_newScope_purgesOtherScopes() {
        store.put(SCOPE_A, "GET", url(1), entry(body = "{\"account\":\"a\"}"))

        store.put(SCOPE_B, "GET", url(2), entry(body = "{\"account\":\"b\"}"))

        assertNull("切账号后不得残留旧作用域条目", store.get(SCOPE_A, "GET", url(1)))
        assertEquals("{\"account\":\"b\"}", store.get(SCOPE_B, "GET", url(2))?.body)
    }

    @Test
    fun clearSessionCache_removesAllScopes() {
        store.put(SCOPE_A, "GET", url(1), entry())
        store.put(SCOPE_B, "GET", url(2), entry())

        runBlocking { store.clearSessionCache() }

        assertNull(store.get(SCOPE_A, "GET", url(1)))
        assertNull(store.get(SCOPE_B, "GET", url(2)))
    }

    @Test
    fun get_expiredEntry_isDroppedAndDeleted() {
        store =
            RoomEtagStore(
                dao,
                clock = { now },
                policy = EtagCachePolicy(ttlMillis = 1_000L),
            )
        store.put(SCOPE_A, "GET", url(1), entry())

        now += 999L
        assertEquals(entry(), store.get(SCOPE_A, "GET", url(1)))

        now += 1L
        assertNull("超过 TTL 应视为未命中", store.get(SCOPE_A, "GET", url(1)))
        assertEquals("过期条目应被删除", 0, rowCount())
    }

    @Test
    fun put_entryBeyondMaxEntryBytes_isSkipped() {
        store =
            RoomEtagStore(
                dao,
                clock = { now },
                policy = EtagCachePolicy(maxEntryBytes = 4L),
            )

        store.put(SCOPE_A, "GET", url(1), entry(body = "hello"))

        assertEquals(0, rowCount())
        assertNull(store.get(SCOPE_A, "GET", url(1)))
    }

    @Test
    fun put_beyondMaxEntries_evictsLeastRecentlyUsed() {
        store =
            RoomEtagStore(
                dao,
                clock = { now },
                policy = EtagCachePolicy(maxEntries = 2),
            )

        now = 1L
        store.put(SCOPE_A, "GET", url(1), entry(body = "one"))
        now = 2L
        store.put(SCOPE_A, "GET", url(2), entry(body = "two"))
        now = 3L
        store.put(SCOPE_A, "GET", url(3), entry(body = "three"))

        assertNull(store.get(SCOPE_A, "GET", url(1)))
        assertEquals("two", store.get(SCOPE_A, "GET", url(2))?.body)
        assertEquals("three", store.get(SCOPE_A, "GET", url(3))?.body)
    }

    @Test
    fun get_touchesLastAccessedAt_protectingFromEviction() {
        store =
            RoomEtagStore(
                dao,
                clock = { now },
                policy = EtagCachePolicy(maxEntries = 2),
            )
        now = 1L
        store.put(SCOPE_A, "GET", url(1), entry(body = "one"))
        now = 2L
        store.put(SCOPE_A, "GET", url(2), entry(body = "two"))

        now = 3L
        store.get(SCOPE_A, "GET", url(1))

        now = 4L
        store.put(SCOPE_A, "GET", url(3), entry(body = "three"))

        assertEquals("最近访问过的条目不应被驱逐", "one", store.get(SCOPE_A, "GET", url(1))?.body)
        assertNull("冷条目应被驱逐", store.get(SCOPE_A, "GET", url(2)))
        assertEquals("three", store.get(SCOPE_A, "GET", url(3))?.body)
    }

    @Test
    fun put_beyondMaxTotalBytes_evictsUntilUnderLimit() {
        store =
            RoomEtagStore(
                dao,
                clock = { now },
                policy = EtagCachePolicy(maxTotalBytes = 10L),
            )
        now = 1L
        store.put(SCOPE_A, "GET", url(1), entry(body = "AAAAAAAA"))
        now = 2L
        store.put(SCOPE_A, "GET", url(2), entry(body = "BBBBBBBB"))

        assertNull("超字节上界后最旧条目应被驱逐", store.get(SCOPE_A, "GET", url(1)))
        assertEquals("BBBBBBBB", store.get(SCOPE_A, "GET", url(2))?.body)
    }

    @Test
    fun put_sameKey_overwritesBodyAndStoredAt() {
        now = 5L
        store.put(SCOPE_A, "GET", url(1), entry(etag = "old", body = "old-body"))
        now = 9L
        store.put(SCOPE_A, "GET", url(1), entry(etag = "new", body = "new-body"))

        assertEquals("new", store.get(SCOPE_A, "GET", url(1))?.etag)
        assertEquals(1, rowCount())
    }

    private fun rowCount(): Int = runBlocking { dao.count() }

    private fun entry(
        etag: String = "W/\"etag\"",
        contentType: String? = "application/json",
        body: String = "{\"id\":1}",
    ): EtagEntry = EtagEntry(etag = etag, contentType = contentType, body = body)

    private fun url(id: Int): String = "https://api.github.com/resource/$id"

    private companion object {
        const val SCOPE_A = "account-a"
        const val SCOPE_B = "account-b"
    }
}
