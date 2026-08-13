package com.yumiru11.githubapp.feature.notifications.data

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
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
 * NotificationsPagingSource 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：DTO → 领域模型映射、过滤参数（all/participating/mention 客户端过滤）、
 * 分页 key 推进、HTTP 错误 → LoadResult.Error。
 */
class NotificationsPagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var api: NotificationApi

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
    fun load_filterAll_mapsItemsAndRequestsAllParam() =
        runTest {
            server.enqueue(
                jsonResponse(
                    "[${notificationJson("1", reason = "mention")}, ${notificationJson("2", unread = false)}]",
                ),
            )

            val result = source(NotificationFilter.ALL).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(2, page.data.size)
            val first = page.data[0]
            assertEquals("1", first.id)
            assertEquals("octocat/Hello-World", first.repoFullName)
            assertEquals("Greetings", first.subjectTitle)
            assertEquals("Issue", first.subjectType)
            assertEquals("mention", first.reason)
            assertTrue(first.unread)
            assertEquals("2026-08-01T10:00:00Z", first.updatedAt)
            assertEquals("https://github.com/octocat/Hello-World/issues/1347", first.htmlUrl)

            val request = server.takeRequest()
            assertEquals("/notifications", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("all"))
            assertNull(request.url.queryParameter("participating"))
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun load_filterParticipating_sendsParticipatingParamOnly() =
        runTest {
            server.enqueue(jsonResponse("[]"))

            val result = source(NotificationFilter.PARTICIPATING).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val request = server.takeRequest()
            assertEquals("true", request.url.queryParameter("participating"))
            assertNull(request.url.queryParameter("all"))
        }

    @Test
    fun load_filterMention_filtersByReasonAndRequestsAll() =
        runTest {
            server.enqueue(
                jsonResponse(
                    "[${notificationJson("1", reason = "mention")}, " +
                        "${notificationJson("2", reason = "subscribed")}, " +
                        "${notificationJson("3", reason = "team_mention")}]",
                ),
            )

            val result = source(NotificationFilter.MENTION).load(refreshParams())

            assertTrue(result is LoadResult.Page)
            val page = result as LoadResult.Page
            assertEquals(listOf("1", "3"), page.data.map { it.id })
            assertEquals("true", server.takeRequest().url.queryParameter("all"))
        }

    @Test
    fun load_append_advancesPageKey() =
        runTest {
            server.enqueue(jsonResponse("[${notificationJson("1")}]"))

            val result = source(NotificationFilter.ALL).load(appendParams(key = 2))

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

            val result = source(NotificationFilter.ALL).load(refreshParams())

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

            val result = source(NotificationFilter.ALL).load(refreshParams())

            assertTrue(result is LoadResult.Error)
        }

    private fun source(filter: NotificationFilter): NotificationsPagingSource = NotificationsPagingSource(api, filter)

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false)

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(key = key, loadSize = 30, placeholdersEnabled = false)
}

/** 构造指向 MockWebServer 的 NotificationApi（复用 core:github-rest 工厂，零真实网络） */
private fun createApi(server: MockWebServer): NotificationApi {
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
    return retrofit.create(NotificationApi::class.java)
}

private fun jsonResponse(body: String): MockResponse =
    MockResponse
        .Builder()
        .body(body)
        .addHeader("Content-Type", "application/json")
        .build()

/** 构造 GitHub 通知 JSON 对象（字段为 DTO 子集） */
private fun notificationJson(
    id: String,
    reason: String = "subscribed",
    unread: Boolean = true,
    title: String = "Greetings",
    type: String = "Issue",
    repo: String = "octocat/Hello-World",
    updatedAt: String = "2026-08-01T10:00:00Z",
    htmlUrl: String = "https://github.com/octocat/Hello-World/issues/1347",
): String =
    """
    {
      "id": "$id",
      "repository": { "full_name": "$repo", "html_url": "https://github.com/$repo" },
      "subject": { "title": "$title", "url": "https://api.github.com/repos/$repo/issues/1347", "type": "$type" },
      "reason": "$reason",
      "unread": $unread,
      "updated_at": "$updatedAt",
      "last_read_at": null,
      "url": "https://api.github.com/notifications/threads/$id",
      "html_url": "$htmlUrl"
    }
    """.trimIndent()
