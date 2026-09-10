package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.githubrest.model.CreateCommentRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateIssueRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateReactionRequest
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionRequest
import com.yumiru11.githubapp.core.githubrest.model.UpdateIssueRequest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * [IssueApi] 写操作集成测试（T14，MockWebServer）。
 *
 * 覆盖：createIssue/updateIssue 请求体与路径、updateIssue 仅序列化非空字段
 * （避免 GitHub null 清空语义误伤）、评论增改删、反应增删（squirrel-girl preview Accept）、
 * 403/404 错误码。
 */
class IssueApiWriteTest {
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
    fun createIssue_fullRequest_sendsPostWithTitleBodyLabels() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"id": 1, "number": 42, "title": "New bug", "state": "open", "body": "Details"}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue =
                issueApi.createIssue(
                    "octocat",
                    "Hello-World",
                    CreateIssueRequest(title = "New bug", body = "Details", labels = listOf("bug")),
                )

            assertEquals(42, issue.number)
            assertEquals("New bug", issue.title)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/repos/octocat/Hello-World/issues", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertTrue("请求体应含 title", body.contains("\"title\":\"New bug\""))
            assertTrue("请求体应含 body", body.contains("\"body\":\"Details\""))
            assertTrue("请求体应含 labels", body.contains("\"labels\":[\"bug\"]"))
        }

    @Test
    fun createIssue_minimalRequest_omitsNullFields() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 1, "title": "t", "state": "open"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            issueApi.createIssue("octocat", "Hello-World", CreateIssueRequest(title = "t"))

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue("仅 title 应被序列化", body.contains("\"title\":\"t\""))
            assertTrue("body 为 null 不应序列化", !body.contains("body"))
            assertTrue("labels 为 null 不应序列化", !body.contains("labels"))
        }

    @Test
    fun updateIssue_stateOnly_serializesOnlyState() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "closed"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            issueApi.updateIssue("octocat", "Hello-World", 42, UpdateIssueRequest(state = "closed"))

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertTrue("应含 state", body.contains("\"state\":\"closed\""))
            assertTrue("title 未变更不应序列化", !body.contains("title"))
            assertTrue("labels 未变更不应序列化", !body.contains("labels"))
            assertTrue("assignees 未变更不应序列化", !body.contains("assignees"))
            assertTrue("milestone 未变更不应序列化", !body.contains("milestone"))
        }

    @Test
    fun updateIssue_titleBodyLabels_sendsAllProvidedFields() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "new", "state": "open"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            issueApi.updateIssue(
                "octocat",
                "Hello-World",
                42,
                UpdateIssueRequest(title = "new", body = "b", labels = listOf("bug", "ui")),
            )

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue("应含 title", body.contains("\"title\":\"new\""))
            assertTrue("应含 body", body.contains("\"body\":\"b\""))
            assertTrue("应含 labels", body.contains("\"labels\":[\"bug\",\"ui\"]"))
        }

    @Test
    fun createComment_validRequest_postsBodyAndParsesComment() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"id": 100, "body": "Nice work", "user": {"login": "octocat", "id": 1},
                         "html_url": "https://github.com/o/r/issues/42#issuecomment-100",
                         "created_at": "2026-08-01T00:00:00Z"}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val comment = issueApi.createComment("octocat", "Hello-World", 42, CreateCommentRequest(body = "Nice work"))

            assertEquals(100L, comment.id)
            assertEquals("Nice work", comment.body)
            assertEquals("octocat", comment.user?.login)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42/comments", request.url.encodedPath)
            assertTrue(
                request.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"body\":\"Nice work\""),
            )
        }

    @Test
    fun updateComment_validRequest_patchesCommentPath() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 100, "body": "Edited"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val comment = issueApi.updateComment("octocat", "Hello-World", 100, CreateCommentRequest(body = "Edited"))

            assertEquals("Edited", comment.body)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/comments/100", request.url.encodedPath)
        }

    @Test
    fun deleteComment_validRequest_sendsDelete() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 204 No Content")
                    .build(),
            )

            val response = issueApi.deleteComment("octocat", "Hello-World", 100)

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/comments/100", request.url.encodedPath)
        }

    @Test
    fun addIssueReaction_validRequest_sendsPreviewAcceptAndParsesReaction() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 7, "content": "heart", "user": {"login": "octocat", "id": 1}}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val reaction = issueApi.addIssueReaction("octocat", "Hello-World", 42, CreateReactionRequest(content = "heart"))

            assertEquals(7L, reaction.id)
            assertEquals("heart", reaction.content)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42/reactions", request.url.encodedPath)
            assertEquals("application/vnd.github.squirrel-girl-preview+json", request.headers["Accept"])
            assertTrue(
                request.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"content\":\"heart\""),
            )
        }

    @Test
    fun removeIssueReaction_validRequest_sendsDeleteWithPreviewAccept() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 204 No Content")
                    .build(),
            )

            val response = issueApi.removeIssueReaction("octocat", "Hello-World", 42, 7)

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42/reactions/7", request.url.encodedPath)
            assertEquals("application/vnd.github.squirrel-girl-preview+json", request.headers["Accept"])
        }

    @Test
    fun addCommentReaction_validRequest_postsToCommentReactionsPath() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 8, "content": "+1", "user": {"login": "octocat", "id": 1}}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val reaction = issueApi.addCommentReaction("octocat", "Hello-World", 100, CreateReactionRequest(content = "+1"))

            assertEquals("+1", reaction.content)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues/comments/100/reactions", request.url.encodedPath)
        }

    @Test
    fun removeCommentReaction_validRequest_sendsDeleteToCommentReactionsPath() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 204 No Content")
                    .build(),
            )

            val response = issueApi.removeCommentReaction("octocat", "Hello-World", 100, 8)

            assertTrue(response.isSuccessful)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues/comments/100/reactions/8", request.url.encodedPath)
        }

    @Test
    fun createIssue_403Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("""{"message":"Repository access blocked"}""")
                    .build(),
            )

            try {
                issueApi.createIssue("octocat", "restricted", CreateIssueRequest(title = "t"))
                throw AssertionError("403 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(403, e.code())
            }
        }

    @Test
    fun updateIssue_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                issueApi.updateIssue("octocat", "Hello-World", 999, UpdateIssueRequest(title = "t"))
                throw AssertionError("404 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun createComment_422Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 422 Unprocessable Entity")
                    .body("""{"message":"Validation Failed"}""")
                    .build(),
            )

            try {
                issueApi.createComment("octocat", "Hello-World", 42, CreateCommentRequest(body = ""))
                throw AssertionError("422 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(422, e.code())
            }
        }

    @Test
    fun updateIssue_optionalFieldsMissing_parsesWithDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "open"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue = issueApi.updateIssue("octocat", "Hello-World", 42, UpdateIssueRequest(title = "t"))

            assertEquals("t", issue.title)
            assertNull(issue.body)
            assertNull(issue.reactions)
        }

    // ── #163 L01：Issue 级订阅 ─────────────────────────────────────

    @Test
    fun getIssueSubscription_subscribed_returnsTrue() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"subscribed": true, "ignored": false}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val subscription = issueApi.getIssueSubscription("octocat", "Hello-World", 42)

            assertTrue(subscription.subscribed)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42/subscription", request.url.encodedPath)
        }

    @Test
    fun getIssueSubscription_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                issueApi.getIssueSubscription("octocat", "Hello-World", 42)
                throw AssertionError("404 应抛 HttpException（未订阅语义）")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun subscribeIssue_sendsPutWithSubscribedTrue() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"subscribed": true}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            issueApi.subscribeIssue("octocat", "Hello-World", 42, SubscriptionRequest(subscribed = true))

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42/subscription", request.url.encodedPath)
            assertEquals("""{"subscribed":true}""", request.body?.utf8())
        }

    @Test
    fun unsubscribeIssue_sendsDelete() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            issueApi.unsubscribeIssue("octocat", "Hello-World", 42)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42/subscription", request.url.encodedPath)
        }

    // ── #163 L02：元数据编辑候选项（只读端点） ─────────────────────

    @Test
    fun listLabels_returnsNameColorDescription() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [{"name": "bug", "color": "d73a4a", "description": "Something isn't working"},
                         {"name": "ui", "color": "1d76db"}]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val labels = issueApi.listLabels("octocat", "Hello-World")

            assertEquals(2, labels.size)
            assertEquals("bug", labels[0].name)
            assertEquals("d73a4a", labels[0].color)
            assertEquals("Something isn't working", labels[0].description)
            assertNull(labels[1].description)
            assertEquals("/repos/octocat/Hello-World/labels", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listAssignees_returnsUsers() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""[{"login": "octocat", "id": 1, "avatar_url": "https://a/o.png"}]""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val assignees = issueApi.listAssignees("octocat", "Hello-World")

            assertEquals(1, assignees.size)
            assertEquals("octocat", assignees.first().login)
            assertEquals("/repos/octocat/Hello-World/assignees", server.takeRequest().url.encodedPath)
        }

    @Test
    fun listAssignees_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                issueApi.listAssignees("octocat", "Hello-World")
                throw AssertionError("404 应抛 HttpException（无权限语义）")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun listMilestones_sendsStateAllAndParsesNumberDueOn() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [{"number": 3, "title": "v1.0", "state": "open", "due_on": "2026-12-31T00:00:00Z"}]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val milestones = issueApi.listMilestones("octocat", "Hello-World")

            assertEquals(1, milestones.size)
            assertEquals(3L, milestones.first().number)
            assertEquals("v1.0", milestones.first().title)
            assertEquals("2026-12-31T00:00:00Z", milestones.first().dueOn)
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/milestones", request.url.encodedPath)
            assertEquals("all", request.url.queryParameter("state"))
        }

    @Test
    fun updateIssue_clearMilestone_serializesExplicitNull() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "open"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            issueApi.updateIssue("octocat", "Hello-World", 42, UpdateIssueRequest(clearMilestone = true))

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertEquals("""{"milestone":null}""", body)
        }
}
