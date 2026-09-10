package com.yumiru11.githubapp.feature.search.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SearchResultCache 单测（issue #165 / L13）：命中 / 过期 / LRU 上限 / 覆盖 / 清理。
 *
 * 时钟注入（clock 参数）→ 过期判定完全确定，不依赖真实时间。
 */
class SearchResultCacheTest {
    @Test
    fun get_missingKey_returnsNull() =
        runTest {
            assertNull(SearchResultCache().get<String>("repos:kotlin", 1))
        }

    @Test
    fun putThenGet_returnsItemsHasMoreAndFlags() =
        runTest {
            val cache = SearchResultCache()
            cache.put("repos:kotlin", page = 1, items = listOf("a", "b"), hasMore = true)

            val page = cache.get<String>("repos:kotlin", 1)

            assertEquals(listOf("a", "b"), page?.items)
            assertTrue(page?.hasMore == true)
            assertFalse(page?.revalidated == true)
        }

    @Test
    fun put_revalidatedFlag_isPersisted() =
        runTest {
            val cache = SearchResultCache()
            cache.put("repos:kotlin", page = 1, items = listOf("a"), hasMore = true, revalidated = true)

            assertTrue(cache.get<String>("repos:kotlin", 1)?.revalidated == true)
        }

    @Test
    fun put_sameKeyAndPage_overwritesPreviousItems() =
        runTest {
            val cache = SearchResultCache()
            cache.put("repos:kotlin", page = 1, items = listOf("old"), hasMore = true)
            cache.put("repos:kotlin", page = 1, items = listOf("new"), hasMore = false)

            val page = cache.get<String>("repos:kotlin", 1)

            assertEquals(listOf("new"), page?.items)
            assertFalse(page?.hasMore == true)
            assertEquals(1, cache.pageCount("repos:kotlin"))
        }

    @Test
    fun get_entryOlderThanTtl_returnsNullAndEvicts() =
        runTest {
            var now = 0L
            val cache = SearchResultCache(ttlMillis = 1_000L, clock = { now })
            cache.put("repos:kotlin", page = 1, items = listOf("a"), hasMore = true)

            now = 1_001L

            assertNull(cache.get<String>("repos:kotlin", 1))
            assertEquals(0, cache.pageCount("repos:kotlin"))
        }

    @Test
    fun get_entryExactlyAtTtl_isStillFresh() =
        runTest {
            var now = 0L
            val cache = SearchResultCache(ttlMillis = 1_000L, clock = { now })
            cache.put("repos:kotlin", page = 1, items = listOf("a"), hasMore = true)

            now = 1_000L

            assertEquals(listOf("a"), cache.get<String>("repos:kotlin", 1)?.items)
        }

    @Test
    fun put_beyondMaxQueries_evictsLeastRecentlyUsedQuery() =
        runTest {
            val cache = SearchResultCache(maxQueries = 2)
            cache.put("repos:a", page = 1, items = listOf("a"), hasMore = true)
            cache.put("repos:b", page = 1, items = listOf("b"), hasMore = true)
            // 命中 a → a 变为最近使用
            cache.get<String>("repos:a", 1)

            cache.put("repos:c", page = 1, items = listOf("c"), hasMore = true)

            assertEquals(2, cache.queryCount())
            assertNull(cache.get<String>("repos:b", 1))
            assertEquals(listOf("a"), cache.get<String>("repos:a", 1)?.items)
            assertEquals(listOf("c"), cache.get<String>("repos:c", 1)?.items)
        }

    @Test
    fun invalidate_dropsOnlyTargetQuery() =
        runTest {
            val cache = SearchResultCache()
            cache.put("repos:kotlin", page = 1, items = listOf("a"), hasMore = true)
            cache.put("users:kotlin", page = 1, items = listOf("u"), hasMore = true)

            cache.invalidate("repos:kotlin")

            assertNull(cache.get<String>("repos:kotlin", 1))
            assertEquals(listOf("u"), cache.get<String>("users:kotlin", 1)?.items)
        }

    @Test
    fun clear_dropsEveryQuery() =
        runTest {
            val cache = SearchResultCache()
            cache.put("repos:kotlin", page = 1, items = listOf("a"), hasMore = true)
            cache.put("repos:kotlin", page = 2, items = listOf("b"), hasMore = true)

            cache.clear()

            assertEquals(0, cache.queryCount())
            assertNull(cache.get<String>("repos:kotlin", 1))
        }

    @Test
    fun defaultMaxQueries_matchesIssueAcceptance() {
        assertEquals(20, SearchResultCache.MAX_QUERIES)
    }
}
