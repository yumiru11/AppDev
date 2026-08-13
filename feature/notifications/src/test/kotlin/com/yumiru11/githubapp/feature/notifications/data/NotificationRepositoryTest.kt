package com.yumiru11.githubapp.feature.notifications.data

import androidx.paging.testing.asSnapshot
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * NotificationRepository 单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 注意：Pager.flow 先发射空 PagingData 再异步加载，`first()` 会在请求发出前返回；
 * 列表断言用 paging-testing 的 [asSnapshot]（等待加载收敛），已读刷新用活跃收集器 +
 * 带超时 takeRequest（runTest 虚拟时钟会与真实网络 IO 死锁，故用 runBlocking）。
 *
 * 覆盖：分页流构造与请求参数、单条/全部已读 PATCH 路径、已读后列表自动刷新
 * （T19 验收第 2 条状态同步）。
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
    fun notifications_filterAll_emitsItemsAndRequestsAllParam() =
        runBlocking {
            server.enqueue(jsonResponse("[${notificationJson("1")}]"))
            server.enqueue(jsonResponse("[]"))

            val items = repository.notifications(NotificationFilter.ALL).asSnapshot()

            assertEquals(1, items.size)
            assertEquals("1", items[0].id)
            assertEquals("octocat/Hello-World", items[0].repoFullName)
            val request = server.takeRequest()
            assertEquals("/notifications", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("all"))
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
    fun markRead_afterLoad_subsequentLoadReflectsReadState() =
        runBlocking {
            server.enqueue(jsonResponse("[${notificationJson("1")}]"))
            server.enqueue(jsonResponse("[]"))
            val flow = repository.notifications(NotificationFilter.ALL)
            assertEquals(1, flow.asSnapshot().size)

            server.enqueue(patchResponse())
            server.enqueue(jsonResponse("[]"))
            repository.markRead("1")

            // 消费到 PATCH（首次加载的 prefetch GET 可能晚到，顺序不定）
            val patchRequest = takeUntilPatch()
            assertEquals("PATCH", patchRequest.method)
            assertEquals("/notifications/threads/1", patchRequest.url.encodedPath)

            // 已读后重新加载：服务端列表已不含该条目（状态同步）
            assertEquals(0, flow.asSnapshot().size)
        }

    @Test
    fun markAllRead_afterLoad_subsequentLoadReflectsReadState() =
        runBlocking {
            server.enqueue(jsonResponse("[${notificationJson("1")}]"))
            server.enqueue(jsonResponse("[]"))
            val flow = repository.notifications(NotificationFilter.ALL)
            assertEquals(1, flow.asSnapshot().size)

            server.enqueue(patchResponse())
            server.enqueue(jsonResponse("[]"))
            repository.markAllRead()

            val patchRequest = takeUntilPatch()
            assertEquals("PATCH", patchRequest.method)
            assertEquals("/notifications", patchRequest.url.encodedPath)

            assertEquals(0, flow.asSnapshot().size)
        }

    @Test
    fun notifications_filterParticipating_requestsParticipatingParam() =
        runBlocking {
            server.enqueue(jsonResponse("[]"))

            repository.notifications(NotificationFilter.PARTICIPATING).asSnapshot()

            val request = server.takeRequest()
            assertEquals("true", request.url.queryParameter("participating"))
            assertTrue(request.url.queryParameter("all") == null)
        }

    /** 消费请求队列直到出现 PATCH（首次加载的 prefetch GET 可能晚到，顺序不定） */
    private fun takeUntilPatch(): RecordedRequest {
        var attempts = 0
        while (attempts < 5) {
            attempts += 1
            val request = server.takeRequest(5, TimeUnit.SECONDS) ?: break
            if (request.method == "PATCH") return request
        }
        throw AssertionError("未收到 PATCH 请求")
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
