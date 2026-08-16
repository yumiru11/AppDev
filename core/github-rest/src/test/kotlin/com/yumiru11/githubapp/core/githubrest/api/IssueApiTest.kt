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
 * [IssueApi] 集成测试（checklist C1，T13 端点此前无独立测试）。
 *
 * 覆盖：listIssues 默认/自定义查询参数、getIssue 路径与 pullRequest 判别字段、
 * listTimeline 混合项解析与 mockingbird preview Accept、400/403/404 错误码、DTO 可选字段默认值。
 */
class IssueApiTest {
    private lateinit var server: MockWebServer
    private lateinit var issueApi: IssueApi

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
        issueApi = retrofit.create(IssueApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun listIssues_defaultParams_sendsStatePagePerPageDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issues = issueApi.listIssues("octocat", "Hello-World")

            assertEquals(0, issues.size)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues", request.url.encodedPath)
            assertEquals("open", request.url.queryParameter("state"))
            assertEquals("1", request.url.queryParameter("page"))
            assertEquals("30", request.url.queryParameter("per_page"))
        }

    @Test
    fun listIssues_customParamsAndDto_sendsQueryAndParsesNested() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": 1,
                            "number": 42,
                            "title": "Bug: crash on startup",
                            "state": "closed",
                            "body": "Repro steps...",
                            "user": { "login": "octocat", "id": 1 },
                            "labels": [
                              { "name": "bug", "color": "d73a4a" },
                              { "name": "priority:high", "color": "b60205" }
                            ],
                            "assignees": [ { "login": "torvalds", "id": 2 } ],
                            "milestone": { "title": "v1.0", "state": "open", "description": "Release" },
                            "reactions": { "total_count": 5 },
                            "comments": 3,
                            "created_at": "2026-08-01T00:00:00Z",
                            "html_url": "https://github.com/octocat/Hello-World/issues/42"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issues = issueApi.listIssues("octocat", "Hello-World", state = "closed", page = 2, perPage = 50)

            val issue = issues.single()
            assertEquals(42, issue.number)
            assertEquals("closed", issue.state)
            assertEquals("Bug: crash on startup", issue.title)
            assertEquals("octocat", issue.user?.login)
            assertEquals(listOf("bug", "priority:high"), issue.labels.map { it.name })
            assertEquals("d73a4a", issue.labels.first().color)
            assertEquals("torvalds", issue.assignees.single().login)
            assertEquals("v1.0", issue.milestone?.title)
            assertEquals(5, issue.reactions?.totalCount)
            assertEquals(3, issue.comments)
            assertEquals("2026-08-01T00:00:00Z", issue.createdAt)
            assertEquals("https://github.com/octocat/Hello-World/issues/42", issue.htmlUrl)
            assertNull("非 PR 列表项 pullRequest 应为 null", issue.pullRequest)

            val request = server.takeRequest()
            assertEquals("closed", request.url.queryParameter("state"))
            assertEquals("2", request.url.queryParameter("page"))
            assertEquals("50", request.url.queryParameter("per_page"))
        }

    @Test
    fun getIssue_validResponse_mapsDtoAndPathAndPullRequestMarker() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "id": 1,
                          "number": 42,
                          "title": "PR: add feature",
                          "state": "open",
                          "pull_request": { "url": "https://api.github.com/repos/o/r/pulls/42" }
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue = issueApi.getIssue("octocat", "Hello-World", number = 42)

            assertEquals(42, issue.number)
            assertEquals("PR: add feature", issue.title)
            assertTrue("pull_request 存在说明该 issue 实为 PR", issue.pullRequest != null)
            assertEquals("/repos/octocat/Hello-World/issues/42", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listTimeline_mixedItems_parsesCommentsEventsAndCrossReferences() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": 1,
                            "event": "commented",
                            "actor": { "login": "octocat", "id": 1 },
                            "body": "First comment",
                            "html_url": "https://github.com/o/r/issues/42#issuecomment-1",
                            "created_at": "2026-08-01T00:00:00Z"
                          },
                          {
                            "id": 2,
                            "event": "closed",
                            "actor": { "login": "torvalds", "id": 2 },
                            "commit_id": "abc123",
                            "commit_url": "https://github.com/o/r/commit/abc123"
                          },
                          {
                            "id": 3,
                            "event": "cross-referenced",
                            "actor": { "login": "dev", "id": 3 },
                            "source": {
                              "issue": { "id": 10, "number": 100, "title": "Related issue", "state": "open" }
                            }
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val events = issueApi.listTimeline("octocat", "Hello-World", number = 42)

            assertEquals(3, events.size)
            assertEquals(listOf("commented", "closed", "cross-referenced"), events.map { it.event })
            assertEquals("First comment", events[0].body)
            assertEquals("octocat", events[0].actor?.login)
            assertEquals("abc123", events[1].commitId)
            assertEquals("https://github.com/o/r/commit/abc123", events[1].commitUrl)
            assertEquals(100, events[2].source?.issue?.number)
            assertEquals("Related issue", events[2].source?.issue?.title)

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues/42/timeline", request.url.encodedPath)
            // 时间线端点必须携带 mockingbird preview Accept（拦截器按设计保留显式 Accept）
            assertEquals("application/vnd.github.mockingbird-preview+json", request.headers["Accept"])
        }

    @Test
    fun listIssues_400Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 400 Bad Request")
                    .body("""{"message":"Only open or closed states can be specified"}""")
                    .build(),
            )

            try {
                issueApi.listIssues("octocat", "Hello-World", state = "invalid")
                fail("400 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(400, e.code())
            }
        }

    @Test
    fun getIssue_403Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("""{"message":"Repository access blocked"}""")
                    .build(),
            )

            try {
                issueApi.getIssue("octocat", "restricted", number = 1)
                fail("403 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(403, e.code())
            }
        }

    @Test
    fun getIssue_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                issueApi.getIssue("octocat", "Hello-World", number = 999)
                fail("404 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun listIssues_optionalFieldsMissing_parsesWithDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          { "id": 1, "number": 2, "title": "minimal", "state": "open" }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue = issueApi.listIssues("octocat", "Hello-World").single()

            assertEquals("minimal", issue.title)
            assertNull("body 缺失应解析为 null", issue.body)
            assertNull("user 缺失应解析为 null", issue.user)
            assertNull("milestone 缺失应解析为 null", issue.milestone)
            assertNull("reactions 缺失应解析为 null", issue.reactions)
            assertNull("createdAt 缺失应解析为 null", issue.createdAt)
            assertEquals("labels 缺失应回退空列表", 0, issue.labels.size)
            assertEquals("assignees 缺失应回退空列表", 0, issue.assignees.size)
            assertEquals("comments 缺失应回退 0", 0, issue.comments)
        }
}
