package com.yumiru11.githubapp.feature.search.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/**
 * SearchPagingSource 单测（loader 注入假数据源，零网络）。
 *
 * 覆盖：首页/中间页/末页分页 key 推进、空页终止、loader 抛 429 → LoadResult.Error
 * 携带归一化 RateLimited、refresh key 计算；以及 issue #165 / L13 的结果缓存
 * （命中零网络、TTL 过期出网、命中写回缓存、后台静默刷新后 invalidate 且自我终止）。
 */
class SearchPagingSourceTest {
    @Test
    fun load_firstPage_setsNextKeyToPageTwo() =
        runTest {
            val source = source(items = listOf("a", "b", "c"))

            val result = source.load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(listOf("a", "b", "c"), page.data)
            assertNull(page.prevKey)
            assertEquals(2, page.nextKey)
        }

    @Test
    fun load_middlePage_setsPrevAndNextKeys() =
        runTest {
            val source = source(items = listOf("a", "b", "c"))

            val result = source.load(appendParams(key = 2))

            val page = result as LoadResult.Page
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
        }

    @Test
    fun load_lastPage_emptyItems_stopsPaging() =
        runTest {
            val source = source(items = emptyList())

            val result = source.load(appendParams(key = 3))

            val page = result as LoadResult.Page
            assertTrue(page.data.isEmpty())
            assertNull(page.nextKey)
        }

    @Test
    fun load_pageSize_usesRequestedLoadSize() =
        runTest {
            val loadSizes = mutableListOf<Int>()
            val source =
                SearchPagingSource<String>(
                    loader = { _, perPage ->
                        loadSizes += perPage
                        listOf("x")
                    },
                )

            source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 15, placeholdersEnabled = false))

            assertEquals(listOf(15), loadSizes)
        }

    @Test
    fun load_http429_normalizedToRateLimitedError() =
        runTest {
            val source = source(loader = { _, _ -> throw httpException(429) })

            val result = source.load(refreshParams())

            assertTrue(result is LoadResult.Error)
            val error = (result as LoadResult.Error).throwable
            assertTrue(error is GitHubRequestException)
            assertTrue((error as GitHubRequestException).error is GitHubError.RateLimited)
        }

    @Test
    fun load_ioException_normalizedToNetworkError() =
        runTest {
            val source = source(loader = { _, _ -> throw java.io.IOException("connection reset") })

            val result = source.load(refreshParams())

            assertTrue(result is LoadResult.Error)
            val error = (result as LoadResult.Error).throwable as GitHubRequestException
            assertTrue(error.error is GitHubError.Network)
        }

    @Test
    fun getRefreshKey_returnsKeyAroundAnchor() =
        runTest {
            val source = source(items = listOf("a"))
            val state =
                PagingState(
                    pages = listOf(PagingSource.LoadResult.Page(data = listOf("a", "b"), prevKey = null, nextKey = 2)),
                    anchorPosition = 1,
                    config = PagingConfig(pageSize = 30),
                    leadingPlaceholderCount = 0,
                )

            // 单页已加载（prevKey=null）：refresh key 取 nextKey-1 = 首页 key（同 FeedPagingSource 先例）
            assertEquals(1, source.getRefreshKey(state))
        }

    @Test
    fun getRefreshKey_noAnchor_returnsNull() =
        runTest {
            val source = source(items = listOf("a"))

            assertNull(
                source.getRefreshKey(
                    PagingState(emptyList(), anchorPosition = null, config = PagingConfig(pageSize = 30), leadingPlaceholderCount = 0),
                ),
            )
        }

    // ── 结果缓存（issue #165 / L13） ──────────────────────────────────────

    @Test
    fun load_freshCacheHit_returnsCachedItemsWithoutCallingLoader() =
        runTest {
            val cache = SearchResultCache()
            cache.put(CACHE_KEY, page = 1, items = listOf("cached"), hasMore = true)
            var calls = 0

            val result = cachedSource(cache) { calls++ }.load(refreshParams())

            val page = result as LoadResult.Page
            assertEquals(listOf("cached"), page.data)
            assertEquals(2, page.nextKey)
            assertEquals(0, calls)
        }

    @Test
    fun load_cachedLastPage_returnsNullNextKey() =
        runTest {
            val cache = SearchResultCache()
            cache.put(CACHE_KEY, page = 1, items = listOf("only"), hasMore = false)

            val page = cachedSource(cache) { 0 }.load(refreshParams()) as LoadResult.Page

            assertNull(page.nextKey)
        }

    @Test
    fun load_expiredCacheEntry_fetchesFromNetworkAndRewritesCache() =
        runTest {
            var now = 0L
            val cache = SearchResultCache(ttlMillis = 1_000L, clock = { now })
            cache.put(CACHE_KEY, page = 1, items = listOf("stale"), hasMore = true)
            var calls = 0
            now = 5_000L

            val page = cachedSource(cache) { calls++ }.load(refreshParams()) as LoadResult.Page

            assertEquals(listOf("fresh"), page.data)
            assertEquals(1, calls)
            assertEquals(listOf("fresh"), cache.get<String>(CACHE_KEY, 1)?.items)
        }

    @Test
    fun load_cacheMiss_writesFetchedPageIntoCache() =
        runTest {
            val cache = SearchResultCache()

            cachedSource(cache) { 0 }.load(refreshParams())

            assertEquals(listOf("fresh"), cache.get<String>(CACHE_KEY, 1)?.items)
        }

    @Test
    fun load_freshCacheHit_silentlyRefreshesInBackgroundThenInvalidates() =
        runTest {
            val cache = SearchResultCache()
            cache.put(CACHE_KEY, page = 1, items = listOf("stale"), hasMore = true)
            var calls = 0
            val source = cachedSource(cache, refreshScope = this) { calls++ }
            var invalidations = 0
            source.registerInvalidatedCallback { invalidations++ }

            val page = source.load(refreshParams()) as LoadResult.Page
            // 命中即时返回缓存（用户先看到内容），此时尚未出网
            assertEquals(listOf("stale"), page.data)
            assertEquals(0, calls)

            runCurrent()

            assertEquals(1, calls)
            assertEquals(listOf("fresh"), cache.get<String>(CACHE_KEY, 1)?.items)
            assertTrue("静默刷新成功后应 invalidate 触发重载", invalidations == 1)
            assertTrue("刷新结果需标记 revalidated 以自我终止", cache.get<String>(CACHE_KEY, 1)?.revalidated == true)
        }

    @Test
    fun load_revalidatedCacheHit_doesNotRefreshAgain() =
        runTest {
            val cache = SearchResultCache()
            cache.put(CACHE_KEY, page = 1, items = listOf("stale"), hasMore = true, revalidated = true)
            var calls = 0
            val source = cachedSource(cache, refreshScope = this) { calls++ }
            var invalidations = 0
            source.registerInvalidatedCallback { invalidations++ }

            source.load(refreshParams())
            runCurrent()

            assertEquals("已 revalidate 的条目不得再次触发静默刷新（否则 invalidate 会无限循环）", 0, calls)
            assertEquals(0, invalidations)
        }

    @Test
    fun load_cacheHitWithoutRefreshScope_doesNotLaunchRefresh() =
        runTest {
            val cache = SearchResultCache()
            cache.put(CACHE_KEY, page = 1, items = listOf("stale"), hasMore = true)
            var calls = 0

            cachedSource(cache, refreshScope = null) { calls++ }.load(refreshParams())
            runCurrent()

            assertEquals(0, calls)
        }

    @Test
    fun load_silentRefreshFails_keepsCachedData() =
        runTest {
            val cache = SearchResultCache()
            cache.put(CACHE_KEY, page = 1, items = listOf("stale"), hasMore = true)
            val source =
                SearchPagingSource<String>(
                    loader = { _, _ -> throw java.io.IOException("offline") },
                    cache = cache,
                    cacheKey = CACHE_KEY,
                    refreshScope = this,
                )

            val page = source.load(refreshParams()) as LoadResult.Page
            runCurrent()

            assertEquals(listOf("stale"), page.data)
            assertEquals(listOf("stale"), cache.get<String>(CACHE_KEY, 1)?.items)
        }

    private fun cachedSource(
        cache: SearchResultCache,
        refreshScope: kotlinx.coroutines.CoroutineScope? = null,
        onLoad: () -> Unit = {},
    ): SearchPagingSource<String> =
        SearchPagingSource(
            loader = { _, _ ->
                onLoad()
                listOf("fresh")
            },
            cache = cache,
            cacheKey = CACHE_KEY,
            refreshScope = refreshScope,
        )

    private fun source(
        items: List<String> = emptyList(),
        loader: suspend (page: Int, perPage: Int) -> List<String> = { _, _ -> items },
    ): SearchPagingSource<String> = SearchPagingSource(loader)

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false)

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(key = key, loadSize = 30, placeholdersEnabled = false)

    private fun httpException(code: Int): HttpException {
        val body = """{"message":"error"}""".toResponseBody("application/json".toMediaType())
        val rawResponse =
            okhttp3.Response
                .Builder()
                .request(
                    okhttp3.Request
                        .Builder()
                        .url("http://localhost/")
                        .build(),
                ).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message("error")
                .body(body)
                .build()
        return HttpException(retrofit2.Response.error<Any>(body, rawResponse))
    }

    private companion object {
        const val CACHE_KEY = "REPOSITORIES:kotlin"
    }
}
