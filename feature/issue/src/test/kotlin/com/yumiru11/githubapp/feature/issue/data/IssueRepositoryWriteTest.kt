package com.yumiru11.githubapp.feature.issue.data

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubgraphql.generated.IssueWriteContextQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.UpdateIssueMutation
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext
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
 * [IssueRepository] 写操作单测（T14）。
 *
 * REST 写方法走 MockWebServer（真实 IssueApi）；GraphQL 通道用 MockK 桩 ApolloClient，
 * 覆盖：写上下文 GraphQL 失败 → 保守空上下文、任务列表 GraphQL 失败 → REST 兜底、
 * 评论/反应/关闭重开走 REST 端点。
 */
class IssueRepositoryWriteTest {
    private lateinit var server: MockWebServer
    private lateinit var issueApi: IssueApi
    private lateinit var apolloClient: ApolloClient

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
        apolloClient = mockk()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository(): IssueRepository = IssueRepository(issueApi, apolloClient)

    @Test
    fun createIssue_validRequest_returnsDomainIssue() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "New", "state": "open", "body": "b"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue = repository().createIssue("octocat", "Hello-World", "New", "b", listOf("bug"))

            assertEquals(42, issue.number)
            assertEquals("New", issue.title)
            assertEquals(IssueState.OPEN, issue.state)
            assertEquals("/repos/octocat/Hello-World/issues", server.takeRequest().url.encodedPath)
        }

    @Test
    fun updateIssue_stateClosed_mapsClosedState() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "closed"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue = repository().updateIssue("octocat", "Hello-World", 42, state = "closed")

            assertEquals(IssueState.CLOSED, issue.state)
        }

    @Test
    fun createComment_validRequest_returnsDomainComment() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"id": 100, "body": "Nice", "user": {"login": "octocat", "id": 1},
                         "html_url": "https://github.com/o/r/issues/42#issuecomment-100",
                         "created_at": "2026-08-01T00:00:00Z"}
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val comment = repository().createComment("octocat", "Hello-World", 42, "Nice")

            assertEquals(100L, comment.id)
            assertEquals("Nice", comment.body)
            assertEquals("octocat", comment.author?.login)
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

            repository().deleteComment("octocat", "Hello-World", 100)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/comments/100", request.url.encodedPath)
        }

    @Test
    fun addIssueReaction_validRequest_returnsReaction() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 7, "content": "heart", "user": {"login": "octocat", "id": 1}}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val reaction = repository().addIssueReaction("octocat", "Hello-World", 42, "heart")

            assertEquals(7L, reaction.id)
            assertEquals("heart", reaction.content)
        }

    @Test
    fun getIssueWriteContext_graphqlThrows_returnsConservativeEmptyContext() =
        runTest {
            coEvery { apolloClient.query(any<IssueWriteContextQuery>()) } throws IOException("graphql down")

            val context = repository().getIssueWriteContext("octocat", "Hello-World", 42)

            assertNull("GraphQL 失败不应有 viewer login", context.viewerLogin)
            assertEquals("GraphQL 失败应保守为 NONE 权限", IssueViewerPermission.NONE, context.viewerPermission)
            assertNull(context.issueNodeId)
        }

    @Test
    fun toggleTaskListItem_graphqlThrows_fallsBackToRestPatch() =
        runTest {
            // GraphQL mutation 抛异常 → 降级 REST PATCH
            coEvery { apolloClient.mutation(any<UpdateIssueMutation>()) } throws IOException("graphql down")
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "open", "body": "- [x] done"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue =
                repository().toggleTaskListItem(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    nodeId = "I_kwDOA",
                    body = "- [ ] done",
                    index = 0,
                    checked = true,
                )

            assertEquals("- [x] done", issue.body)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/repos/octocat/Hello-World/issues/42", request.url.encodedPath)
            assertTrue(
                "REST 兜底应携带翻转后的 body",
                request.body
                    ?.utf8()
                    .orEmpty()
                    .contains("- [x] done"),
            )
        }

    @Test
    fun toggleTaskListItem_nodeIdNull_usesRestDirectly() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "open", "body": "- [ ] done"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue =
                repository().toggleTaskListItem(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    nodeId = null,
                    body = "- [x] done",
                    index = 0,
                    checked = false,
                )

            assertEquals("- [ ] done", issue.body)
            assertEquals("PATCH", server.takeRequest().method)
        }

    @Test
    fun toggleTaskListItem_indexOutOfRange_returnsOriginalBody() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "open", "body": "- [ ] only"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val issue =
                repository().toggleTaskListItem(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    nodeId = null,
                    body = "- [ ] only",
                    index = 9,
                    checked = true,
                )

            assertEquals("- [ ] only", issue.body)
        }

    @Test
    fun updateIssueMeta_labelsOnly_sendsLabelsPatch() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"id": 1, "number": 42, "title": "t", "state": "open"}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            repository().updateIssueMeta("octocat", "Hello-World", 42, labels = listOf("bug"))

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue("应含 labels", body.contains("\"labels\":[\"bug\"]"))
            assertTrue("assignees 未变更不应序列化", !body.contains("assignees"))
            assertTrue("milestone 未变更不应序列化", !body.contains("milestone"))
        }

    @Test
    fun issueWriteContext_defaults_conservative() {
        val context = IssueWriteContext()
        assertEquals(IssueViewerPermission.NONE, context.viewerPermission)
        assertNull(context.viewerLogin)
        assertNull(context.issueNodeId)
    }
}
