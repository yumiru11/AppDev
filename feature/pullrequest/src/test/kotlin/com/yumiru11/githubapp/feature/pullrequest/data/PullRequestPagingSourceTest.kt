package com.yumiru11.githubapp.feature.pullrequest.data

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * PullRequestPagingSource 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：列表映射领域模型、分页 key 推进、空页 nextKey 置空、IO/HTTP 错误 → LoadResult.Error。
 */
class PullRequestPagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PullRequestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = createApi(server)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun load_success_mapsToDomainAndRequestsCorrectPath() =
        runTest {
            server.enqueue(jsonResponse("[${pullRequestJson("1")}]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.data.size)
            val item = page.data[0]
            assertEquals(1L, item.id)
            assertEquals(42, item.number)
            assertEquals("Add feature", item.title)
            assertEquals(PullRequestState.OPEN, item.state)
            assertEquals("octocat", item.author?.login)
            assertEquals(3, item.commentCount)
            assertNull(page.prevKey)
            // 末页不满一页 → 判定无更多数据，nextKey 置空（避免多请求一次空页）
            assertNull(page.nextKey)

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/pulls", request.url.encodedPath)
            assertEquals("open", request.url.queryParameter("state"))
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun load_append_fullPage_advancesPageKey() =
        runTest {
            server.enqueue(jsonResponse("[${(1..30).joinToString(",") { pullRequestJson(it.toString()) }}]"))

            val result = source().load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(30, page.data.size)
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
        }

    @Test
    fun load_append_partialPage_nextKeyNull() =
        runTest {
            server.enqueue(jsonResponse("[${pullRequestJson("1")}]"))

            val result = source().load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.data.size)
            assertEquals(1, page.prevKey)
            assertNull("不满一页 → 无更多数据", page.nextKey)
        }

    @Test
    fun load_closedFilter_sendsClosedState() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = PullRequestPagingSource(api, "octocat", "Hello-World", PullRequestFilter.CLOSED).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            assertEquals("closed", server.takeRequest().url.queryParameter("state"))
        }

    @Test
    fun load_allFilter_sendsAllState() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = PullRequestPagingSource(api, "octocat", "Hello-World", PullRequestFilter.ALL).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            assertEquals("all", server.takeRequest().url.queryParameter("state"))
        }

    @Test
    fun load_ioError_returnsErrorResult() =
        runTest {
            val failingApi =
                mockk<PullRequestApi> {
                    coEvery { listPullRequests(any(), any(), any(), any(), any()) } throws IOException("boom")
                }

            val result = PullRequestPagingSource(failingApi, "octocat", "Hello-World", PullRequestFilter.OPEN).load(refreshParams())

            assertTrue(result is LoadResult.Error)
        }

    @Test
    fun load_httpError_returnsErrorResult() =
        runTest {
            val httpException =
                mockk<retrofit2.HttpException> {
                    every { code() } returns 500
                }
            val failingApi =
                mockk<PullRequestApi> {
                    coEvery { listPullRequests(any(), any(), any(), any(), any()) } throws httpException
                }

            val result = PullRequestPagingSource(failingApi, "octocat", "Hello-World", PullRequestFilter.OPEN).load(refreshParams())

            assertTrue(result is LoadResult.Error)
        }

    private fun source(): PullRequestPagingSource = PullRequestPagingSource(api, "octocat", "Hello-World", PullRequestFilter.OPEN)

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> = PagingSource.LoadParams.Refresh(null, 30, false)

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> = PagingSource.LoadParams.Append(key, 30, false)

    private fun createApi(server: MockWebServer): PullRequestApi {
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client =
                    GitHubRestClient.createOkHttpClient(
                        tokenProvider = GuestTokenProvider(),
                        etagStore = InMemoryEtagStore(),
                        debugLogging = false,
                    ),
                json = GitHubRestClient.createJson(),
            )
        return retrofit.create(PullRequestApi::class.java)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse
            .Builder()
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build()

    private fun pullRequestJson(id: String): String =
        """
        {
          "id": $id,
          "number": 42,
          "title": "Add feature",
          "state": "open",
          "body": "Description",
          "user": { "login": "octocat", "id": 1 },
          "comments": 3,
          "created_at": "2026-08-01T00:00:00Z",
          "html_url": "https://github.com/octocat/Hello-World/pull/42"
        }
        """.trimIndent()
}
