package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * [NotificationApi] 集成测试（checklist C1，T19 端点此前无独立测试）。
 *
 * 覆盖：all/participating 参数省略与携带、嵌套 DTO 解析、PATCH 已读（205 空体）、
 * 401 错误码、可选字段默认值。
 */
class NotificationApiTest {
    private lateinit var server: MockWebServer
    private lateinit var notificationApi: NotificationApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
        notificationApi = retrofit.create(NotificationApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun listNotifications_defaultParams_omitsAllAndParticipating() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val notifications = notificationApi.listNotifications()

            assertEquals(0, notifications.size)
            val request = server.takeRequest()
            assertEquals("/notifications", request.url.encodedPath)
            // null 参数不携带查询串：all/participating 必须缺席
            assertNull("all=null 不应出现在查询串", request.url.queryParameter("all"))
            assertNull("participating=null 不应出现在查询串", request.url.queryParameter("participating"))
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun listNotifications_allTrueParticipatingFalse_sendsBooleanParams() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            notificationApi.listNotifications(all = true, participating = false)

            val request = server.takeRequest()
            assertEquals("true", request.url.queryParameter("all"))
            assertEquals("false", request.url.queryParameter("participating"))
        }

    @Test
    fun listNotifications_validResponse_mapsNestedDto() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": "1",
                            "repository": {
                              "full_name": "octocat/Hello-World",
                              "html_url": "https://github.com/octocat/Hello-World"
                            },
                            "subject": {
                              "title": "Bug: crash",
                              "url": "https://api.github.com/repos/o/r/issues/42",
                              "latest_comment_url": "https://api.github.com/repos/o/r/issues/comments/7",
                              "type": "Issue"
                            },
                            "reason": "mention",
                            "unread": false,
                            "updated_at": "2026-08-01T00:00:00Z",
                            "last_read_at": "2026-08-01T01:00:00Z",
                            "url": "https://api.github.com/notifications/threads/1",
                            "html_url": "https://github.com/octocat/Hello-World/issues/42#issuecomment-7"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val notification = notificationApi.listNotifications().single()

            assertEquals("1", notification.id)
            assertEquals("octocat/Hello-World", notification.repository.fullName)
            assertEquals("https://github.com/octocat/Hello-World", notification.repository.htmlUrl)
            assertEquals("Bug: crash", notification.subject.title)
            assertEquals("Issue", notification.subject.type)
            assertEquals("https://api.github.com/repos/o/r/issues/42", notification.subject.url)
            assertEquals("mention", notification.reason)
            assertEquals(false, notification.unread)
            assertEquals("2026-08-01T00:00:00Z", notification.updatedAt)
            assertEquals("2026-08-01T01:00:00Z", notification.lastReadAt)
            assertEquals("https://github.com/octocat/Hello-World/issues/42#issuecomment-7", notification.htmlUrl)
        }

    @Test
    fun markThreadRead_205Response_returnsSuccessful() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 205 Reset Content")
                    .body("")
                    .build(),
            )

            val response = notificationApi.markThreadRead(threadId = "12345")

            assertTrue("205 应视为成功响应", response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/notifications/threads/12345", request.url.encodedPath)
        }

    @Test
    fun markAllRead_205Response_returnsSuccessful() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 205 Reset Content")
                    .body("")
                    .build(),
            )

            val response = notificationApi.markAllRead()

            assertTrue("205 应视为成功响应", response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/notifications", request.url.encodedPath)
        }

    @Test
    fun listNotifications_401Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("""{"message":"Bad credentials"}""")
                    .build(),
            )

            try {
                notificationApi.listNotifications()
                fail("401 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(401, e.code())
            }
        }

    @Test
    fun listNotifications_optionalFieldsMissing_parsesWithDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": "1",
                            "repository": {},
                            "subject": { "title": "minimal", "url": "https://api.github.com/x", "type": "Issue" },
                            "reason": "subscribed"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val notification = notificationApi.listNotifications().single()

            assertNull("repository.fullName 缺失应解析为 null", notification.repository.fullName)
            assertNull("repository.htmlUrl 缺失应解析为 null", notification.repository.htmlUrl)
            assertNull("subject.latestCommentUrl 缺失应解析为 null", notification.subject.latestCommentUrl)
            assertEquals("unread 缺失应回退 true", true, notification.unread)
            assertNull("updatedAt 缺失应解析为 null", notification.updatedAt)
            assertNull("htmlUrl 缺失应解析为 null", notification.htmlUrl)
        }
}
