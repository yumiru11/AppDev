package com.yumiru11.githubapp.feature.pullrequest.data

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubgraphql.generated.PullRequestReviewThreadsQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerQuery
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.GitRefApi
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.core.githubrest.api.RepoManagementApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * [PullRequestRepository] 写操作单测（#163 L03，MockWebServer 真实 PullRequestApi）。
 *
 * 覆盖：updatePr 只发送变更字段（PATCH /pulls/{number}）、closePr（state=closed）、
 * reopenPr（state=open）、失败（422）异常透出。
 */
class PullRequestRepositoryWriteTest {
    private lateinit var server: MockWebServer
    private lateinit var pullRequestApi: PullRequestApi
    private lateinit var issueApi: IssueApi
    private lateinit var userApi: UserApi
    private lateinit var apollo: ApolloClient
    private lateinit var tokenStorage: InMemoryTokenStorage

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
        pullRequestApi = retrofit.create(PullRequestApi::class.java)
        issueApi = retrofit.create(IssueApi::class.java)
        userApi = retrofit.create(UserApi::class.java)
        apollo = mockk()
        tokenStorage = InMemoryTokenStorage()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository(): PullRequestRepository =
        PullRequestRepository(
            pullRequestApi = pullRequestApi,
            repositoryApi = mockk<RepositoryApi>(),
            repoManagementApi = mockk<RepoManagementApi>(),
            gitRefApi = mockk<GitRefApi>(),
            issueApi = issueApi,
            apolloClient = apollo,
            userApi = userApi,
            tokenStorage = tokenStorage,
        )

    private fun enqueuePullRequest(
        state: String,
        title: String = "T",
        mergedAt: String? = null,
    ) {
        val merged = mergedAt?.let { ""","merged_at": "$it"""" }.orEmpty()
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"id": 1, "number": 42, "title": "$title", "state": "$state"$merged}""")
                .addHeader("Content-Type", "application/json")
                .build(),
        )
    }

    @Test
    fun updatePr_titleAndBody_sendsPatchWithChangedFieldsOnly() =
        runTest {
            enqueuePullRequest(state = "open", title = "New title")

            val updated = repository().updatePr("octocat", "Hello-World", 42, title = "New title", body = "New body")

            assertEquals("New title", updated.title)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/repos/octocat/Hello-World/pulls/42", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertTrue(body.contains(""""title":"New title""""))
            assertTrue(body.contains(""""body":"New body""""))
            assertFalse("未变更的 state 不应携带", body.contains("state"))
        }

    @Test
    fun closePr_sendsStateClosed_mapsClosedState() =
        runTest {
            enqueuePullRequest(state = "closed")

            val closed = repository().closePr("octocat", "Hello-World", 42)

            assertEquals(PullRequestState.CLOSED, closed.state)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("""{"state":"closed"}""", request.body?.utf8())
        }

    @Test
    fun reopenPr_sendsStateOpen_mapsOpenState() =
        runTest {
            enqueuePullRequest(state = "open")

            val reopened = repository().reopenPr("octocat", "Hello-World", 42)

            assertEquals(PullRequestState.OPEN, reopened.state)
            assertEquals("""{"state":"open"}""", server.takeRequest().body?.utf8())
        }

    @Test
    fun updatePr_422Response_propagatesHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 422 Unprocessable Entity")
                    .body("""{"message":"Validation Failed"}""")
                    .build(),
            )

            try {
                repository().updatePr("octocat", "Hello-World", 42, title = "")
                throw AssertionError("422 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(422, e.code())
            }
        }

    // ── PR 会话评论（#166：补上此前"写接口尚未接入"的缺口）──────────────────
    @Test
    fun viewerLoginOrNull_graphqlThrows_returnsNull() =
        runTest {
            // 未登录 / GraphQL 降级：返回 null，UI 据此整体隐藏编辑/删除菜单
            coEvery { apollo.query(any<ViewerQuery>()) } throws IOException("graphql down")

            assertNull(repository().viewerLoginOrNull())
        }

    @Test
    fun updateComment_patchesCommentBody() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 9, "body": "edited"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            repository().updateComment("octocat", "Hello-World", 9L, "edited")

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues/comments/9", request.url.encodedPath)
            assertEquals("PATCH", request.method)
            assertEquals("""{"body":"edited"}""", request.body?.utf8())
        }

    @Test
    fun deleteComment_sendsDeleteToCommentEndpoint() =
        runTest {
            server.enqueue(MockResponse.Builder().status("HTTP/1.1 204 No Content").build())

            repository().deleteComment("octocat", "Hello-World", 9L)

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/issues/comments/9", request.url.encodedPath)
            assertEquals("DELETE", request.method)
        }

    @Test
    fun addComment_postsToIssueCommentsEndpointWithBody() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 9, "body": "LGTM"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            repository().addComment("octocat", "Hello-World", 42, "LGTM")

            val request = server.takeRequest()
            // PR 在 REST 语义里就是 issue：会话评论走 issues/{number}/comments
            assertEquals("/repos/octocat/Hello-World/issues/42/comments", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"body":"LGTM"}""", request.body?.utf8())
        }

    @Test
    fun addComment_403Response_propagatesHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("""{"message":"Resource not accessible by integration"}""")
                    .build(),
            )

            try {
                repository().addComment("octocat", "Hello-World", 42, "x")
                throw AssertionError("403 应抛 HttpException（上层据此提示失败且不清空输入）")
            } catch (e: HttpException) {
                assertEquals(403, e.code())
            }
        }

    // ── PAT 降级（isRestOnly）：REST 补位通道 ────────────────────────

    @Test
    fun viewerLoginOrNull_restOnlyMode_usesRestUserEndpointWithoutGraphQl() =
        runTest {
            tokenStorage.saveSession(SessionData(pat = "github_pat_test", isRestOnly = true))
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"login":"octocat","id":1}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            assertEquals("octocat", repository().viewerLoginOrNull())
            verify(exactly = 0) { apollo.query(any<ViewerQuery>()) }
            assertEquals("/user", server.takeRequest().url.encodedPath)
        }

    @Test
    fun viewerLoginOrNull_restOnlyMode_restUnauthorized_returnsNull() =
        runTest {
            tokenStorage.saveSession(SessionData(pat = "github_pat_test", isRestOnly = true))
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("{}")
                    .build(),
            )

            assertNull(repository().viewerLoginOrNull())
            verify(exactly = 0) { apollo.query(any<ViewerQuery>()) }
        }

    @Test
    fun reviewThreadContext_restOnlyMode_returnsEmptyContextWithoutGraphQl() =
        runTest {
            tokenStorage.saveSession(SessionData(pat = "github_pat_test", isRestOnly = true))

            val context = repository().reviewThreadContext("PR_kwDOA")

            // REST 无 reviewThreads 等价端点：保守空上下文 → UI 隐藏解析入口
            assertNull(context.pullRequestNodeId)
            assertTrue(context.threads.isEmpty())
            verify(exactly = 0) { apollo.query(any<PullRequestReviewThreadsQuery>()) }
        }

    @Test
    fun setThreadResolved_restOnlyMode_throwsRestOnlyUnsupportedException() =
        runTest {
            tokenStorage.saveSession(SessionData(pat = "github_pat_test", isRestOnly = true))

            // 防御性兜底：即使 UI 入口越权可达，也必须以失败告终（调用方回滚乐观更新）
            assertFailsWith<RestOnlyUnsupportedException> { repository().setThreadResolved("THREAD_1", true) }
        }
}
