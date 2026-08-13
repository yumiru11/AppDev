package com.yumiru11.githubapp.feature.issue.data

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import com.yumiru11.githubapp.feature.issue.model.IssueState
import io.mockk.coEvery
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
 * IssuePagingSource 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：列表映射领域模型、分页 key 推进、空页 nextKey 置空、IO/HTTP 错误 → LoadResult.Error。
 */
class IssuePagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var api: IssueApi

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
            server.enqueue(jsonResponse("[${issueJson("1")}]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.data.size)
            val item = page.data[0]
            assertEquals(1L, item.id)
            assertEquals(42, item.number)
            assertEquals("Bug report", item.title)
            assertEquals(IssueState.OPEN, item.state)
            assertEquals("octocat", item.author?.login)
            assertEquals(3, item.commentCount)
            assertNull(page.prevKey)
            // 末页不满一页 → 判定无更多数据，nextKey 置空（避免多请求一次空页）
            assertNull(page.nextKey)

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues", request.url.encodedPath)
            assertEquals("open", request.url.queryParameter("state"))
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun load_append_fullPage_advancesPageKey() =
        runTest {
            server.enqueue(jsonResponse("[${(1..30).joinToString(",") { issueJson(it.toString()) }}]"))

            val result = source().load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun load_append_partialPage_returnsNullNextKey() =
        runTest {
            server.enqueue(jsonResponse("[${issueJson("1")}]"))

            val result = source().load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertNull(page.nextKey)
        }

    @Test
    fun load_emptyPage_returnsNullNextKey() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(0, page.data.size)
            assertNull(page.nextKey)
            assertNull(page.prevKey)
        }

    @Test
    fun load_ioError_returnsLoadResultError() =
        runTest {
            val failingApi =
                mockk<IssueApi> {
                    coEvery {
                        listIssues(any(), any(), any(), any(), any())
                    } throws IOException("network down")
                }

            val result = IssuePagingSource(failingApi, "octocat", "Hello-World", IssueFilter.OPEN).load(refreshParams())

            assertTrue(result is LoadResult.Error)
        }

    @Test
    fun load_httpError_returnsLoadResultError() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build(),
            )

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Error)
        }

    private fun source(): IssuePagingSource = IssuePagingSource(api, "octocat", "Hello-World", IssueFilter.OPEN)

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false)

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(key = key, loadSize = 30, placeholdersEnabled = false)
}

/** 构造指向 MockWebServer 的 IssueApi（复用 core:github-rest 工厂，零真实网络） */
private fun createApi(server: MockWebServer): IssueApi {
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
    return retrofit.create(IssueApi::class.java)
}

private fun jsonResponse(body: String): MockResponse =
    MockResponse
        .Builder()
        .body(body)
        .addHeader("Content-Type", "application/json")
        .build()

/** Issue JSON 骨架（snake_case，GitHubRestClient 已配 SnakeCase 命名策略） */
private fun issueJson(id: String): String =
    """
    {
      "id": $id,
      "number": 42,
      "title": "Bug report",
      "state": "open",
      "body": "Body text",
      "user": { "id": 1, "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/1" },
      "labels": [],
      "assignees": [],
      "comments": 3,
      "html_url": "https://github.com/octocat/Hello-World/issues/42"
    }
    """.trimIndent()
