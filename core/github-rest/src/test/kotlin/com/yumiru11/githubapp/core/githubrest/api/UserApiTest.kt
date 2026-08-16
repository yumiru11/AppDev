package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.GitHubHeaderInterceptor
import com.yumiru11.githubapp.core.githubrest.http.GitHubHeaders
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * [UserApi] 测试补充（checklist C1：请求构造/分页参数/DTO 解析边界/错误码/超时）。
 *
 * 覆盖既有 GitHubRestApiTest 未触及的端点：userRepositories / starred ×2 / followers / following ×2 的
 * 路径与分页参数、可选字段缺失的默认值、401 错误码、空列表与读超时。
 */
class UserApiTest {
    private lateinit var server: MockWebServer
    private lateinit var userApi: UserApi

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
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun userRepositories_validResponse_mapsListAndPagingParamsAndPath() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": 1,
                            "name": "repo-a",
                            "full_name": "octocat/repo-a",
                            "private": false,
                            "owner": { "login": "octocat", "id": 1 },
                            "stargazers_count": 7,
                            "forks_count": 3
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repos = userApi.userRepositories(login = "octocat", perPage = 50, page = 3)

            assertEquals(1, repos.size)
            assertEquals("repo-a", repos.first().name)
            assertEquals("octocat", repos.first().owner.login)
            val request = server.takeRequest()
            assertEquals("/users/octocat/repos", request.url.encodedPath)
            assertEquals("50", request.url.queryParameter("per_page"))
            assertEquals("3", request.url.queryParameter("page"))
        }

    @Test
    fun currentUserStarred_validResponse_mapsListAndPagingParams() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": 2,
                            "name": "starred-repo",
                            "full_name": "octocat/starred-repo",
                            "private": true,
                            "owner": { "login": "octocat", "id": 1 },
                            "language": "Kotlin",
                            "default_branch": "main"
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repos = userApi.currentUserStarred(perPage = 50, page = 3)

            assertEquals("starred-repo", repos.single().name)
            assertEquals(true, repos.single().isPrivate)
            assertEquals("Kotlin", repos.single().language)
            val request = server.takeRequest()
            assertEquals("/user/starred", request.url.encodedPath)
            assertEquals("50", request.url.queryParameter("per_page"))
            assertEquals("3", request.url.queryParameter("page"))
        }

    @Test
    fun userStarred_validResponse_usesLoginInPath() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repos = userApi.userStarred(login = "torvalds", perPage = 30, page = 1)

            assertEquals(0, repos.size)
            val request = server.takeRequest()
            assertEquals("/users/torvalds/starred", request.url.encodedPath)
            assertEquals("30", request.url.queryParameter("per_page"))
            assertEquals("1", request.url.queryParameter("page"))
        }

    @Test
    fun currentUserFollowers_validResponse_mapsList() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          { "login": "follower-1", "id": 2, "avatar_url": "https://a/u/2", "type": "User" }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val followers = userApi.currentUserFollowers(perPage = 30, page = 1)

            assertEquals("follower-1", followers.single().login)
            assertEquals("/user/followers", server.takeRequest().url.encodedPath)
        }

    @Test
    fun currentUserFollowing_validResponse_mapsList() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          { "login": "following-1", "id": 3, "type": "User" }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val following = userApi.currentUserFollowing(perPage = 30, page = 1)

            assertEquals("following-1", following.single().login)
            assertEquals("/user/following", server.takeRequest().url.encodedPath)
        }

    @Test
    fun userFollowing_validResponse_usesLoginInPath() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          { "login": "following-1", "id": 3, "type": "User" }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val following = userApi.userFollowing(login = "octocat", perPage = 30, page = 1)

            assertEquals("following-1", following.single().login)
            assertEquals("/users/octocat/following", server.takeRequest().url.encodedPath)
        }

    @Test
    fun currentUser_optionalFieldsMissing_parsesWithDefaults() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{ "login": "octocat", "id": 1 }""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val user = userApi.currentUser()

            assertEquals("octocat", user.login)
            assertEquals(1L, user.id)
            assertNull("name 缺失应解析为 null", user.name)
            assertNull("avatarUrl 缺失应解析为 null", user.avatarUrl)
            assertNull("type 缺失应解析为 null", user.type)
            assertEquals("统计字段缺失应回退默认 0", 0, user.publicRepos)
            assertEquals(0, user.followers)
            assertEquals(0, user.following)
        }

    @Test
    fun currentUser_401Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("""{"message":"Bad credentials"}""")
                    .build(),
            )

            try {
                userApi.currentUser()
                fail("401 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(401, e.code())
            }
        }

    @Test
    fun currentUserRepositories_emptyListResponse_returnsEmptyList() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("[]")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val repos = userApi.currentUserRepositories(perPage = 30, page = 1)

            assertTrue("空数组应解析为空列表", repos.isEmpty())
            assertEquals("/user/repos", server.takeRequest().url.encodedPath)
        }

    @Test
    fun currentUser_slowResponse_throwsReadTimeout() =
        runTest {
            // 专用短读超时客户端：服务端延迟 3s 出 body，客户端 1s 超时
            val timeoutClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(GitHubHeaderInterceptor())
                    .readTimeout(1, TimeUnit.SECONDS)
                    .build()
            val retrofit =
                GitHubRestClient.createRetrofit(
                    baseUrl = server.url("/"),
                    client = timeoutClient,
                    json = GitHubRestClient.createJson(),
                )
            val timeoutApi = retrofit.create(UserApi::class.java)
            server.enqueue(
                MockResponse
                    .Builder()
                    .bodyDelay(3, TimeUnit.SECONDS)
                    .body("""{ "login": "octocat", "id": 1 }""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            // SocketTimeoutException extends InterruptedIOException
            val result = runCatching { timeoutApi.currentUser() }
            assertTrue(
                "读超时应抛 InterruptedIOException，实际：${result.exceptionOrNull()}",
                result.exceptionOrNull() is InterruptedIOException,
            )

            // 请求头已在超时前送达服务端：路径与统一头可断言
            val recorded = server.takeRequest()
            assertEquals("/user", recorded.url.encodedPath)
            assertEquals(GitHubHeaders.ACCEPT_VALUE, recorded.headers["Accept"])
            assertEquals(GitHubHeaders.API_VERSION_VALUE, recorded.headers["X-GitHub-Api-Version"])
        }
}
