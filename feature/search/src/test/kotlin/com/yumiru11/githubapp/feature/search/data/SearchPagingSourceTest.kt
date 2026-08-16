package com.yumiru11.githubapp.feature.search.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
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
 * 携带归一化 RateLimited、refresh key 计算。
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
                SearchPagingSource<String> { _, perPage ->
                    loadSizes += perPage
                    listOf("x")
                }

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
}
