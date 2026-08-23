package com.yumiru11.githubapp.feature.notifications.data

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * NotificationRepository 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：[NotificationRepository.latest] 快照拉取（参数映射、短页即止、翻页上限、
 * mention 客户端过滤、HTTP 错误抛出）与三个写操作路径（PATCH 已读 / PATCH 全部已读 /
 * DELETE done，#88 左滑删除）。
 */
class NotificationRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = NotificationRepository(createApi(server))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun latest_filterAll_mapsItemsAndRequestsPerPage100() =
        runBlocking {
            server.enqueue(jsonResponse("[${notificationJson("1")}, ${notificationJson("2")}]"))

            val items = repository.latest(NotificationFilter.ALL)

            assertEquals(listOf("1", "2"), items.map { it.id })
            assertEquals("octocat/Hello-World", items[0].repoFullName)
            // 短页（2 < 100）即止：仅一次请求
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("/notifications", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("all"))
            assertEquals("100", request.url.queryParameter("per_page"))
        }

    @Test
    fun latest_fullPage_fetchesSecondPageUntilShortPage() =
        runBlocking {
            val fullPage = (1..100).joinToString(",", "[", "]") { notificationJson(it.toString()) }
            server.enqueue(jsonResponse(fullPage))
            server.enqueue(jsonResponse("[${notificationJson("101")}]"))

            val items = repository.latest(NotificationFilter.ALL)

            assertEquals(101, items.size)
            assertEquals(2, server.requestCount)
            server.takeRequest() // 消费第一页请求
            assertEquals("2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun latest_filterMention_filtersByReasonClientSide() =
        runBlocking {
            server.enqueue(
                jsonResponse(
                    "[${notificationJson("1", reason = "mention")}, " +
                        "${notificationJson("2", reason = "subscribed")}, " +
                        "${notificationJson("3", reason = "team_mention")}]",
                ),
            )

            val items = repository.latest(NotificationFilter.MENTION)

            assertEquals(listOf("1", "3"), items.map { it.id })
        }

    @Test
    fun latest_httpError_throwsOriginalException() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(404).build())

            val exception =
                assertThrows(HttpException::class.java) {
                    runBlocking { repository.latest(NotificationFilter.ALL) }
                }

            assertEquals(404, exception.code())
        }

    @Test
    fun markRead_success_sendsPatchThreadRequest() =
        runBlocking {
            server.enqueue(patchResponse())

            repository.markRead("42")

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/notifications/threads/42", request.url.encodedPath)
        }

    @Test
    fun markAllRead_success_sendsPatchNotificationsRequest() =
        runBlocking {
            server.enqueue(patchResponse())

            repository.markAllRead()

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/notifications", request.url.encodedPath)
        }

    @Test
    fun markDone_success_sendsDeleteThreadRequest() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(204).build())

            repository.markDone("42")

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/notifications/threads/42", request.url.encodedPath)
        }

    private fun patchResponse(): MockResponse =
        MockResponse
            .Builder()
            .status("HTTP/1.1 205 Reset Content")
            .build()
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
