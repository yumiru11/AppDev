package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * SearchApi 集成测试（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：四个端点的请求构造（q/page/per_page）、search 响应包裹（total_count/items）
 * 解析、PR 条目 `pull_request` 字段、代码搜索条目（name/path/repository）、429 → HttpException。
 */
class SearchApiTest {
    private lateinit var server: MockWebServer
    private lateinit var searchApi: SearchApi

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
        searchApi = retrofit.create(SearchApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchRepositories_buildsQueryAndPagingParams() =
        runTest {
            server.enqueue(jsonResponse(repositoriesSearchJson()))

            val response = searchApi.searchRepositories(query = "language:kotlin", page = 2, perPage = 15)

            assertEquals(1, response.totalCount)
            assertEquals("octocat/Hello-World", response.items.single().fullName)

            val request = server.takeRequest()
            assertEquals("/search/repositories", request.url.encodedPath)
            assertEquals("language:kotlin", request.url.queryParameter("q"))
            assertEquals("2", request.url.queryParameter("page"))
            assertEquals("15", request.url.queryParameter("per_page"))
        }

    @Test
    fun searchRepositories_emptyItems_defaultsToEmptyList() =
        runTest {
            server.enqueue(jsonResponse("""{"total_count": 0, "incomplete_results": false, "items": []}"""))

            val response = searchApi.searchRepositories(query = "nothing")

            assertEquals(0, response.totalCount)
            assertTrue(response.items.isEmpty())
        }

    @Test
    fun searchUsers_parsesSearchEnvelopeAndItems() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "total_count": 1,
                      "incomplete_results": false,
                      "items": [
                        {"login": "octocat", "id": 1, "avatar_url": "https://avatars.githubusercontent.com/u/1"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val response = searchApi.searchUsers(query = "octocat")

            assertEquals(1, response.totalCount)
            val user = response.items.single()
            assertEquals("octocat", user.login)
            assertEquals("https://avatars.githubusercontent.com/u/1", user.avatarUrl)
        }

    @Test
    fun searchIssues_issueItem_hasNullPullRequestMarker() =
        runTest {
            server.enqueue(jsonResponse(issuesSearchJson(pullRequest = null)))

            val response = searchApi.searchIssues(query = "bug")

            val item = response.items.single()
            assertEquals(42, item.number)
            assertEquals("Bug report", item.title)
            assertEquals("open", item.state)
            assertNull(item.pullRequest)
            assertEquals("https://api.github.com/repos/octocat/Hello-World", item.repositoryUrl)
        }

    @Test
    fun searchIssues_prItem_hasPullRequestMarker() =
        runTest {
            server.enqueue(
                jsonResponse(issuesSearchJson(pullRequest = """{"url": "https://api.github.com/repos/octocat/Hello-World/pulls/42"}""")),
            )

            val response = searchApi.searchIssues(query = "bug")

            val item = response.items.single()
            assertTrue("PR 条目必须带 pull_request 字段", item.pullRequest != null)
        }

    @Test
    fun searchCode_parsesItemWithRepository() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "total_count": 1,
                      "incomplete_results": false,
                      "items": [
                        {
                          "name": "Main.kt",
                          "path": "src/main/kotlin/Main.kt",
                          "sha": "abc123",
                          "html_url": "https://github.com/octocat/Hello-World/blob/main/src/main/kotlin/Main.kt",
                          "repository": {
                            "id": 1,
                            "name": "Hello-World",
                            "full_name": "octocat/Hello-World",
                            "private": false,
                            "owner": {"login": "octocat", "id": 1}
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val response = searchApi.searchCode(query = "fun main")

            assertEquals(1, response.totalCount)
            val item = response.items.single()
            assertEquals("Main.kt", item.name)
            assertEquals("src/main/kotlin/Main.kt", item.path)
            assertEquals("octocat/Hello-World", item.repository?.fullName)
            assertFalse(response.incompleteResults)
        }

    @Test
    fun searchRepositories_http429_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(429)
                    .addHeader("Retry-After", "60")
                    .build(),
            )

            try {
                searchApi.searchRepositories(query = "kotlin")
                fail("429 必须抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(429, e.code())
            }
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse
            .Builder()
            .body(body)
            .build()

    private fun repositoriesSearchJson(): String =
        """
        {
          "total_count": 1,
          "incomplete_results": false,
          "items": [
            {
              "id": 1,
              "name": "Hello-World",
              "full_name": "octocat/Hello-World",
              "private": false,
              "owner": {"login": "octocat", "id": 1},
              "description": "My first repo",
              "html_url": "https://github.com/octocat/Hello-World",
              "stargazers_count": 1234,
              "forks_count": 56,
              "language": "Kotlin",
              "default_branch": "main"
            }
          ]
        }
        """.trimIndent()

    private fun issuesSearchJson(pullRequest: String?): String =
        """
        {
          "total_count": 1,
          "incomplete_results": false,
          "items": [
            {
              "id": 100,
              "number": 42,
              "title": "Bug report",
              "state": "open",
              "user": {"login": "octocat", "id": 1},
              "html_url": "https://github.com/octocat/Hello-World/issues/42",
              "repository_url": "https://api.github.com/repos/octocat/Hello-World"
              ${pullRequest?.let { ", \"pull_request\": $it" }.orEmpty()}
            }
          ]
        }
        """.trimIndent()
}
