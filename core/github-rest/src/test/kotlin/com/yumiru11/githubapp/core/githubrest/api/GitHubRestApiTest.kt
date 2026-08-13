package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.GitHubHeaderInterceptor
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Retrofit 3 REST 接口集成测试（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 覆盖：snake_case DTO 映射、Retrofit 3 原生 suspend、非 2xx 自动抛 HttpException。
 */
class GitHubRestApiTest {
    private lateinit var server: MockWebServer
    private lateinit var userApi: UserApi
    private lateinit var repositoryApi: RepositoryApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client =
                    GitHubRestClient
                        .createOkHttpClient(
                            tokenProvider = GuestTokenProvider(),
                            etagStore = InMemoryEtagStore(),
                            debugLogging = false,
                        ).newBuilder()
                        .addInterceptor(GitHubHeaderInterceptor())
                        .build(),
                json = GitHubRestClient.createJson(),
            )
        userApi = retrofit.create(UserApi::class.java)
        repositoryApi = retrofit.create(RepositoryApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun currentUser_validResponse_mapsSnakeCaseDto() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "login": "octocat",
                          "id": 1,
                          "name": "The Octocat",
                          "avatar_url": "https://avatars.githubusercontent.com/u/1",
                          "html_url": "https://github.com/octocat",
                          "bio": "GitHub mascot",
                          "type": "User",
                          "unknown_future_field": "ignored"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val user = userApi.currentUser()

            assertEquals("octocat", user.login)
            assertEquals(1L, user.id)
            assertEquals("The Octocat", user.name)
            assertEquals("https://avatars.githubusercontent.com/u/1", user.avatarUrl)
            assertEquals("https://github.com/octocat", user.htmlUrl)
            assertEquals("GitHub mascot", user.bio)
        }

    @Test
    fun getRepository_validResponse_mapsNestedOwnerAndCounts() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "id": 1296269,
                          "name": "Hello-World",
                          "full_name": "octocat/Hello-World",
                          "private": false,
                          "owner": { "login": "octocat", "id": 1, "avatar_url": "https://a/u/1", "html_url": "https://github.com/octocat" },
                          "description": "My first repository",
                          "html_url": "https://github.com/octocat/Hello-World",
                          "stargazers_count": 1700,
                          "forks_count": 1900,
                          "language": "JavaScript",
                          "default_branch": "master"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repo = repositoryApi.getRepository("octocat", "Hello-World")

            assertEquals("Hello-World", repo.name)
            assertEquals("octocat/Hello-World", repo.fullName)
            assertEquals(false, repo.isPrivate)
            assertEquals("octocat", repo.owner.login)
            assertEquals(1700, repo.stargazersCount)
            assertEquals("JavaScript", repo.language)
            assertEquals("master", repo.defaultBranch)

            // 路径参数正确拼装
            assertEquals("/repos/octocat/Hello-World", server.takeRequest().url.encodedPath)
        }

    @Test
    fun currentUser_404Response_throwsHttpExceptionWithCode() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                userApi.currentUser()
                fail("suspend 调用在 404 时应直接抛 HttpException（Retrofit 3 语义）")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun getRepository_500Response_throwsHttpExceptionWithServerCode() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 502 Bad Gateway")
                    .body("{}")
                    .build(),
            )

            try {
                repositoryApi.getRepository("octocat", "Hello-World")
                fail("5xx 应抛 HttpException")
            } catch (e: HttpException) {
                assertTrue(e.code() in 500..599)
            }
        }

    @Test
    fun getUser_validResponse_mapsStatsFields() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "login": "torvalds",
                          "id": 1024025,
                          "name": "Linus Torvalds",
                          "avatar_url": "https://avatars.githubusercontent.com/u/1024025",
                          "html_url": "https://github.com/torvalds",
                          "bio": "Linux kernel",
                          "type": "User",
                          "public_repos": 8,
                          "followers": 250000,
                          "following": 0
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val user = userApi.getUser("torvalds")

            assertEquals("torvalds", user.login)
            assertEquals(8, user.publicRepos)
            assertEquals(250_000, user.followers)
            assertEquals(0, user.following)
            assertEquals("/users/torvalds", server.takeRequest().url.encodedPath)
        }

    @Test
    fun currentUserRepositories_validResponse_mapsListAndPagingParams() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": 1296269,
                            "name": "Hello-World",
                            "full_name": "octocat/Hello-World",
                            "private": false,
                            "owner": { "login": "octocat", "id": 1, "avatar_url": "https://a/u/1", "html_url": "https://github.com/octocat" },
                            "description": "My first repository",
                            "html_url": "https://github.com/octocat/Hello-World",
                            "stargazers_count": 1700,
                            "forks_count": 1900,
                            "language": "JavaScript",
                            "default_branch": "master"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repos = userApi.currentUserRepositories(perPage = 30, page = 2)

            assertEquals(1, repos.size)
            assertEquals("Hello-World", repos.first().name)
            assertEquals("octocat", repos.first().owner.login)
            val request = server.takeRequest()
            assertEquals("/user/repos", request.url.encodedPath)
            assertEquals("30", request.url.queryParameter("per_page"))
            assertEquals("2", request.url.queryParameter("page"))
        }

    @Test
    fun userFollowers_validResponse_mapsList() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "login": "follower-1",
                            "id": 2,
                            "avatar_url": "https://avatars.githubusercontent.com/u/2",
                            "html_url": "https://github.com/follower-1",
                            "type": "User"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val followers = userApi.userFollowers("octocat", perPage = 30, page = 1)

            assertEquals(1, followers.size)
            assertEquals("follower-1", followers.first().login)
            assertEquals("/users/octocat/followers", server.takeRequest().url.encodedPath)
        }
}
