package com.yumiru11.githubapp.core.githubdata.user

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubgraphql.GitHubApolloClientFactory
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * UserRepository 集成测试（MockWebServer 同服双通道：/graphql 读优先、/user REST 兜底）。
 */
class DefaultUserRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultUserRepository
    private lateinit var apolloClient: ApolloClient
    private val responsesByPath = mutableMapOf<String, MockResponse>()

    @Before
    fun setUp() {
        responsesByPath.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    responsesByPath[request.url.encodedPath]
                        ?: MockResponse
                            .Builder()
                            .status("HTTP/1.1 404 Not Found")
                            .body("{}")
                            .build()
            }
        server.start()

        val okHttpClient =
            GitHubRestClient.createOkHttpClient(
                tokenProvider = TokenProvider { "test-token" },
                etagStore = InMemoryEtagStore(),
                debugLogging = false,
            )
        val apolloClient: ApolloClient =
            GitHubApolloClientFactory.create(
                serverUrl = server.url("/graphql").toString(),
                okHttpClient = OkHttpClient(),
            )
        this.apolloClient = apolloClient
        val retrofit = GitHubRestClient.createRetrofit(server.url("/"), okHttpClient, GitHubRestClient.createJson())
        repository = DefaultUserRepository(apolloClient, retrofit.create(UserApi::class.java))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getCurrentUser_graphqlSuccess_mapsViewerFields() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"viewer":{
                          "__typename":"User",
                          "login":"octocat","name":"The Octocat",
                          "avatarUrl":"https://avatars.githubusercontent.com/u/1",
                          "bio":"GitHub mascot","url":"https://github.com/octocat"
                        }}}
                        """.trimIndent(),
                    ).build()

            val user = repository.getCurrentUser()

            assertEquals("octocat", user.login)
            assertEquals("The Octocat", user.name)
            assertEquals("https://avatars.githubusercontent.com/u/1", user.avatarUrl)
            assertEquals("GitHub mascot", user.bio)
            assertEquals("https://github.com/octocat", user.url)
        }

    @Test
    fun getCurrentUser_graphqlUnauthorized_fallsBackToRest() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("{}")
                    .build()
            responsesByPath["/user"] =
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"login":"octocat","id":1,"name":"The Octocat",
                         "avatar_url":"https://avatars.githubusercontent.com/u/1",
                         "html_url":"https://github.com/octocat","bio":"GitHub mascot"}
                        """.trimIndent(),
                    ).build()

            val user = repository.getCurrentUser()

            assertEquals("octocat", user.login)
            assertEquals("https://github.com/octocat", user.url)
        }

    @Test
    fun getCurrentUser_bothChannelsFail_throwsRequestExceptionWithRestError() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("{}")
                    .build()
            responsesByPath["/user"] =
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 403 Forbidden")
                    .body("{}")
                    .build()

            val exception = assertFailsWith<GitHubRequestException> { repository.getCurrentUser() }

            assertEquals(GitHubError.Forbidden, exception.error)
        }

    @Test
    fun getCurrentUser_graphqlErrorsPayload_fallsBackToRest() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .body("""{"data":null,"errors":[{"message":"Bad credentials","type":"UNAUTHORIZED"}]}""")
                    .build()
            responsesByPath["/user"] =
                MockResponse.Builder().body("""{"login":"octocat","id":1}""").build()

            val user = repository.getCurrentUser()

            assertEquals("octocat", user.login)
        }

    @Test
    fun getCurrentUser_graphqlServerError_fallsBackToRest() =
        runTest {
            // GraphQL 通道 HTTP 5xx（response.exception）→ REST 兜底
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build()
            responsesByPath["/user"] =
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"login":"octocat","id":1,"name":"The Octocat",
                         "avatar_url":"https://avatars.githubusercontent.com/u/1",
                         "html_url":"https://github.com/octocat","bio":"GitHub mascot"}
                        """.trimIndent(),
                    ).build()

            val user = repository.getCurrentUser()

            assertEquals("octocat", user.login)
            assertEquals("https://github.com/octocat", user.url)
        }

    @Test
    fun getCurrentUser_viewerNullWithoutErrors_fallsBackToRest() =
        runTest {
            // data.viewer 为 null 且无 errors 数组（无 viewer 权限等）→ REST 兜底
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .body("""{"data":{"viewer":null}}""")
                    .build()
            responsesByPath["/user"] =
                MockResponse.Builder().body("""{"login":"octocat","id":1}""").build()

            val user = repository.getCurrentUser()

            assertEquals("octocat", user.login)
        }

    @Test
    fun getCurrentUser_restThrowsCancellationException_rethrows() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build()
            val restApi =
                mockk<UserApi> {
                    coEvery { currentUser() } throws CancellationException("cancelled")
                }
            val repoUnderTest = DefaultUserRepository(apolloClient, restApi)

            // 取消异常必须原样上抛（不得包装为 GitHubRequestException）
            assertFailsWith<CancellationException> { repoUnderTest.getCurrentUser() }
        }

    @Test
    fun getCurrentUser_restThrowsIoException_wrapsAsNetworkError() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("{}")
                    .build()
            val restApi =
                mockk<UserApi> {
                    coEvery { currentUser() } throws IOException("network down")
                }
            val repoUnderTest = DefaultUserRepository(apolloClient, restApi)

            val exception = assertFailsWith<GitHubRequestException> { repoUnderTest.getCurrentUser() }

            val networkError = assertIs<GitHubError.Network>(exception.error)
            assertEquals("network down", networkError.cause?.message)
        }
}
