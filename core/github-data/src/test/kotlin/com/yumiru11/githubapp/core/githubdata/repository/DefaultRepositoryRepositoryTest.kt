package com.yumiru11.githubapp.core.githubdata.repository

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubgraphql.GitHubApolloClientFactory
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
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
import kotlin.test.assertFailsWith

/**
 * RepositoryRepository 集成测试（GraphQL 读优先 + REST 兜底，双通道同 MockWebServer）。
 */
class DefaultRepositoryRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultRepositoryRepository
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
        val retrofit = GitHubRestClient.createRetrofit(server.url("/"), okHttpClient, GitHubRestClient.createJson())
        repository = DefaultRepositoryRepository(apolloClient, retrofit.create(RepositoryApi::class.java))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getRepository_graphqlSuccess_mapsOverviewFields() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"data":{"repository":{
                          "__typename":"Repository",
                          "id":"R_kwDOA","name":"Hello-World",
                          "description":"first repo","stargazerCount":3,"forkCount":2,
                          "primaryLanguage":{"__typename":"Language","name":"Kotlin","color":"#A97BFF"},
                          "defaultBranchRef":{"__typename":"Ref","name":"main"},
                          "licenseInfo":{"__typename":"License","name":"MIT License"},
                          "viewerHasStarred":true
                        }}}
                        """.trimIndent(),
                    ).build()

            val repo = repository.getRepository("octocat", "Hello-World")

            assertEquals("octocat", repo.ownerLogin)
            assertEquals("Hello-World", repo.name)
            assertEquals("octocat/Hello-World", repo.fullName)
            assertEquals(3, repo.stargazerCount)
            assertEquals(2, repo.forkCount)
            assertEquals("Kotlin", repo.language)
            assertEquals("main", repo.defaultBranch)
        }

    @Test
    fun getRepository_graphqlNotFound_fallsBackToRest() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .body("""{"data":{"repository":null},"errors":[{"message":"Could not resolve to a Repository"}]}""")
                    .build()
            responsesByPath["/repos/octocat/Hello-World"] =
                MockResponse
                    .Builder()
                    .body(
                        """
                        {"id":1,"name":"Hello-World","full_name":"octocat/Hello-World","private":false,
                         "owner":{"login":"octocat","id":1},
                         "description":"first repo","stargazers_count":3,"forks_count":2,
                         "language":"JavaScript","default_branch":"master"}
                        """.trimIndent(),
                    ).build()

            val repo = repository.getRepository("octocat", "Hello-World")

            assertEquals("octocat", repo.ownerLogin)
            assertEquals("JavaScript", repo.language)
            assertEquals("master", repo.defaultBranch)
        }

    @Test
    fun getRepository_bothChannels404_throwsNotFound() =
        runTest {
            responsesByPath["/graphql"] =
                MockResponse
                    .Builder()
                    .body("""{"data":{"repository":null},"errors":[{"message":"Could not resolve to a Repository"}]}""")
                    .build()
            // /repos/... 未注册 → dispatcher 返回 404

            val exception =
                assertFailsWith<GitHubRequestException> { repository.getRepository("ghost", "missing") }

            assertEquals(GitHubError.NotFound, exception.error)
        }
}
