package com.yumiru11.githubapp.feature.home.data

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.yumiru11.githubapp.core.githubrest.api.EventsApi
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.home.model.FeedEventType
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FeedPagingSource 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：事件类型映射（6 类支持 + 未知过滤）、请求路径与分页参数、
 * 分页 key 推进、push/star 仓库链接兜底、HTTP 错误 → LoadResult.Error。
 */
class FeedPagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var api: EventsApi

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
    fun load_issuesEvent_mapsToDomainAndRequestsCorrectPath() =
        runTest {
            server.enqueue(jsonResponse("[${issuesEventJson("1")}]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.data.size)
            val item = page.data[0]
            assertEquals("1", item.id)
            assertEquals(FeedEventType.ISSUE, item.type)
            assertEquals("octocat", item.actorLogin)
            assertEquals("octocat/Hello-World", item.repoFullName)
            assertEquals("opened", item.action)
            assertEquals("Bug report", item.title)
            assertEquals(42, item.number)
            assertEquals("https://github.com/octocat/Hello-World/issues/42", item.htmlUrl)

            val request = server.takeRequest()
            assertEquals("/users/octocat/received_events", request.url.encodedPath)
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun load_unknownEventType_filteredOut() =
        runTest {
            server.enqueue(
                jsonResponse(
                    "[${issuesEventJson("1")}, ${eventJson("2", type = "DeleteEvent")}]",
                ),
            )

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(listOf("1"), page.data.map { it.id })
        }

    @Test
    fun load_pushEvent_mapsCommitCountAndRepoUrl() =
        runTest {
            server.enqueue(jsonResponse("[${pushEventJson("3")}]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val item = (result as LoadResult.Page).data[0]
            assertEquals(FeedEventType.PUSH, item.type)
            assertEquals(2, item.commitCount)
            assertEquals("fix: something", item.title)
            assertEquals("https://github.com/octocat/Hello-World", item.htmlUrl)
        }

    @Test
    fun load_starEvent_mapsToStarWithRepoUrl() =
        runTest {
            server.enqueue(jsonResponse("[${eventJson("4", type = "WatchEvent")}]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val item = (result as LoadResult.Page).data[0]
            assertEquals(FeedEventType.STAR, item.type)
            assertEquals("", item.title)
            assertNull(item.number)
            assertEquals("https://github.com/octocat/Hello-World", item.htmlUrl)
        }

    @Test
    fun load_issueCommentEvent_prefersIssueHtmlUrlOverCommentFragment() =
        runTest {
            server.enqueue(jsonResponse("[${issueCommentEventJson("5")}]"))

            val result = source().load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val item = (result as LoadResult.Page).data[0]
            assertEquals(FeedEventType.ISSUE_COMMENT, item.type)
            // comment.html_url 带 #issuecomment-* fragment 无法被 LinkParser 解析，优先取 issue 链接
            assertEquals("https://github.com/octocat/Hello-World/issues/42", item.htmlUrl)
        }

    @Test
    fun load_append_advancesPageKey() =
        runTest {
            server.enqueue(jsonResponse("[${issuesEventJson("1")}]"))

            val result = source().load(appendParams(key = 2))

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(1, page.prevKey)
            assertEquals(3, page.nextKey)
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
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

    private fun source(): FeedPagingSource = FeedPagingSource(api, login = "octocat")

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false)

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(key = key, loadSize = 30, placeholdersEnabled = false)
}

/** 构造指向 MockWebServer 的 EventsApi（复用 core:github-rest 工厂，零真实网络） */
private fun createApi(server: MockWebServer): EventsApi {
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
    return retrofit.create(EventsApi::class.java)
}

private fun jsonResponse(body: String): MockResponse =
    MockResponse
        .Builder()
        .body(body)
        .addHeader("Content-Type", "application/json")
        .build()

/** 通用事件 JSON 骨架（无 payload；WatchEvent/ForkEvent/DeleteEvent 等无载荷事件用） */
private fun eventJson(
    id: String,
    type: String,
): String =
    """
    {
      "id": "$id",
      "type": "$type",
      "actor": { "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/1" },
      "repo": { "name": "octocat/Hello-World" },
      "created_at": "2026-08-01T10:00:00Z"
    }
    """.trimIndent()

/** IssuesEvent JSON（opened action + issue 载荷） */
private fun issuesEventJson(id: String): String =
    """
    {
      "id": "$id",
      "type": "IssuesEvent",
      "actor": { "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/1" },
      "repo": { "name": "octocat/Hello-World" },
      "payload": {
        "action": "opened",
        "issue": {
          "title": "Bug report",
          "number": 42,
          "html_url": "https://github.com/octocat/Hello-World/issues/42"
        }
      },
      "created_at": "2026-08-01T10:00:00Z"
    }
    """.trimIndent()

/** PushEvent JSON（2 提交 + size） */
private fun pushEventJson(id: String): String =
    """
    {
      "id": "$id",
      "type": "PushEvent",
      "actor": { "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/1" },
      "repo": { "name": "octocat/Hello-World" },
      "payload": {
        "size": 2,
        "commits": [
          { "message": "fix: something", "sha": "abc123" },
          { "message": "chore: cleanup", "sha": "def456" }
        ]
      },
      "created_at": "2026-08-01T10:00:00Z"
    }
    """.trimIndent()

/** IssueCommentEvent JSON（comment.html_url 带 fragment，验证优先取 issue 链接） */
private fun issueCommentEventJson(id: String): String =
    """
    {
      "id": "$id",
      "type": "IssueCommentEvent",
      "actor": { "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/1" },
      "repo": { "name": "octocat/Hello-World" },
      "payload": {
        "action": "created",
        "issue": {
          "title": "Bug report",
          "number": 42,
          "html_url": "https://github.com/octocat/Hello-World/issues/42"
        },
        "comment": {
          "body": "I can reproduce",
          "html_url": "https://github.com/octocat/Hello-World/issues/42#issuecomment-100"
        }
      },
      "created_at": "2026-08-01T10:00:00Z"
    }
    """.trimIndent()
