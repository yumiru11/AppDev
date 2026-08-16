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
 * [EventsApi] 集成测试（checklist C1，T10 端点此前无独立测试）。
 *
 * 覆盖：received_events 路径与分页参数（默认/自定义）、按事件类型区分的 payload 解析
 * （Push/Issues/Watch/无 payload）、404 错误码、空列表。
 */
class EventsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var eventsApi: EventsApi

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
        eventsApi = retrofit.create(EventsApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun receivedEvents_defaultParams_sendsPathAndPagingDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val events = eventsApi.receivedEvents("octocat")

            assertTrue("空数组应解析为空列表", events.isEmpty())
            val request = server.takeRequest()
            assertEquals("/users/octocat/received_events", request.url.encodedPath)
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun receivedEvents_customParams_sendsValues() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            eventsApi.receivedEvents("octocat", page = 2, perPage = 50)

            val request = server.takeRequest()
            assertEquals("2", request.url.queryParameter("page"))
            assertEquals("50", request.url.queryParameter("per_page"))
        }

    @Test
    fun receivedEvents_pushEventPayload_parsesCommitsAndSize() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": "E1",
                            "type": "PushEvent",
                            "actor": { "login": "octocat", "id": 1, "avatar_url": "https://a/u/1" },
                            "repo": { "name": "octocat/Hello-World" },
                            "payload": {
                              "action": null,
                              "commits": [
                                { "message": "fix: typo", "sha": "abc123" },
                                { "message": "feat: new", "sha": "def456" }
                              ],
                              "size": 2
                            },
                            "created_at": "2026-08-01T00:00:00Z"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val event = eventsApi.receivedEvents("octocat").single()

            assertEquals("PushEvent", event.type)
            assertEquals("octocat", event.actor.login)
            assertEquals("octocat/Hello-World", event.repo.name)
            assertEquals(2, event.payload?.size)
            assertEquals(listOf("fix: typo", "feat: new"), event.payload?.commits?.map { it.message })
            assertEquals(
                "def456",
                event.payload
                    ?.commits
                    ?.get(1)
                    ?.sha,
            )
            assertEquals("2026-08-01T00:00:00Z", event.createdAt)
        }

    @Test
    fun receivedEvents_issuesEventPayload_parsesActionAndIssue() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": "E2",
                            "type": "IssuesEvent",
                            "actor": { "login": "octocat", "id": 1 },
                            "repo": { "name": "octocat/Hello-World" },
                            "payload": {
                              "action": "opened",
                              "issue": {
                                "title": "Bug report",
                                "number": 7,
                                "html_url": "https://github.com/octocat/Hello-World/issues/7"
                              }
                            }
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val event = eventsApi.receivedEvents("octocat").single()

            assertEquals("IssuesEvent", event.type)
            assertEquals("opened", event.payload?.action)
            assertEquals("Bug report", event.payload?.issue?.title)
            assertEquals(7, event.payload?.issue?.number)
            assertEquals("https://github.com/octocat/Hello-World/issues/7", event.payload?.issue?.htmlUrl)
        }

    @Test
    fun receivedEvents_watchEventWithoutPayload_parsesNullPayload() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": "E3",
                            "type": "WatchEvent",
                            "actor": { "login": "octocat", "id": 1 },
                            "repo": { "name": "octocat/Hello-World" }
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val event = eventsApi.receivedEvents("octocat").single()

            assertEquals("WatchEvent", event.type)
            assertNull("无 payload 字段应解析为 null", event.payload)
            assertNull("createdAt 缺失应解析为 null", event.createdAt)
        }

    @Test
    fun receivedEvents_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                eventsApi.receivedEvents("unknown-user")
                fail("404 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }
}
