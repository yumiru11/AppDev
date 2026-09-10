package com.yumiru11.githubapp.feature.pullrequest.data

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.GitRefApi
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.core.githubrest.api.RepoManagementApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * [PullRequestRepository] 写操作单测（#163 L03，MockWebServer 真实 PullRequestApi）。
 *
 * 覆盖：updatePr 只发送变更字段（PATCH /pulls/{number}）、closePr（state=closed）、
 * reopenPr（state=open）、失败（422）异常透出。
 */
class PullRequestRepositoryWriteTest {
    private lateinit var server: MockWebServer
    private lateinit var pullRequestApi: PullRequestApi

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
            apolloClient = mockk<ApolloClient>(),
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
}
