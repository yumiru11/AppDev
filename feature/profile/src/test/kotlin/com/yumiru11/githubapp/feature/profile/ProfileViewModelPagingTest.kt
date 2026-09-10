package com.yumiru11.githubapp.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubrest.api.GistApi
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit

/**
 * ProfileViewModel 四列表 PagingData 流端到端单测（MockWebServer 模拟 GitHub API，零真实网络）。
 *
 * 注意：Pager.flow 先发射空 PagingData 再异步加载，`first()` 会在请求发出前返回；
 * 列表断言用 paging-testing 的 [asSnapshot]（等待加载收敛）；真实网络 IO 与 runTest
 * 虚拟时钟会死锁，故用 runBlocking（同 NotificationRepositoryTest 先例）。
 *
 * 覆盖：资料头（GET /user）与列表（/user/repos 等）编排、分页流首屏数据、空列表终止。
 */
class ProfileViewModelPagingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** 已记录的请求（带 2s 超时，避免队列空时阻塞 —— takeRequest 无超时会挂死）。 */
    private fun recordedRequests(): List<RecordedRequest> {
        val requests = mutableListOf<RecordedRequest>()
        while (true) {
            val request = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS) ?: break
            requests += request
        }
        return requests
    }

    private fun recordedPaths(): List<String> = recordedRequests().map { it.url.encodedPath }

    /**
     * 按「路径 + page 查询参数」应答（#166 / UI21 引入）。
     *
     * 为什么不再用 server.enqueue 的顺序队列：资料头之后多了一次
     * /user/starred?per_page=1 的 Star 总数探测，而它与 Pager 的首屏请求是**并发**发出的 ——
     * 谁先到不保证顺序，队列式应答会随机错位（表现为「分页断言以毫不相关的方式失败」）。
     * 按请求路由则与顺序无关，测试重新变回确定性的。
     */
    private fun routeByPathAndPage(routes: Map<String, String>) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
                    // 三级回退：精确页 → 首页 → 裸路径（探测类端点不带 page 参数）
                    val body =
                        routes["$path?page=$page"]
                            ?: routes["$path?page=1"]
                            ?: routes[path]
                    return if (body == null) {
                        MockResponse
                            .Builder()
                            .status("HTTP/1.1 404 Not Found")
                            .body("{}")
                            .build()
                    } else {
                        MockResponse
                            .Builder()
                            .body(body)
                            .addHeader("Content-Type", "application/json")
                            .build()
                    }
                }
            }
    }

    @Test
    fun repositoriesFlow_collect_loadsFirstPageAndRequestsParams() =
        runBlocking {
            val repos = (1..30).joinToString(",") { repositoryJson("repo-$it") }
            routeByPathAndPage(
                mapOf(
                    "/user" to userJson(),
                    // Star 总数探测（#166 / UI21）：返回值不重要，路由掉即可
                    "/user/starred" to "[]",
                    "/user/repos?page=1" to "[$repos]",
                    // 满页会触发 Pager 预取下一页 → 空页终止
                    "/user/repos?page=2" to "[]",
                ),
            )

            val viewModel = viewModel()

            val items = viewModel.repositories.asSnapshot()

            assertEquals(30, items.size)
            assertEquals("repo-1", items.first().name)
            // 请求集合：资料头 + 列表首屏都发出去了（顺序因 Star 探测并发而不保证，故按集合断言）
            val requests = recordedRequests()
            val paths = requests.map { it.url.encodedPath }
            assertTrue("/user" in paths)
            assertTrue("/user/repos" in paths)
            // 列表请求参数（分页契约）：page=1 & per_page=30
            val listRequest = requests.first { it.url.encodedPath == "/user/repos" }
            assertEquals("1", listRequest.url.queryParameter("page"))
            assertEquals("30", listRequest.url.queryParameter("per_page"))
        }

    @Test
    fun followersFlow_collect_loadsFirstPageUsers() =
        runBlocking {
            val users = (1..30).joinToString(",") { userJson("follower-$it") }
            routeByPathAndPage(
                mapOf(
                    "/user" to userJson(),
                    "/user/starred" to "[]", // Star 总数探测（#166 / UI21）
                    "/user/followers?page=1" to "[$users]",
                    "/user/followers?page=2" to "[]",
                ),
            )

            val viewModel = viewModel()

            val items = viewModel.followers.asSnapshot()

            assertEquals(30, items.size)
            assertEquals("follower-1", items.first().login)
            val paths = recordedPaths()
            assertTrue("/user" in paths)
            assertTrue("/user/followers" in paths)
        }

    @Test
    fun starredFlow_collectEmptyList_returnsEmptySnapshot() =
        runBlocking {
            routeByPathAndPage(
                mapOf(
                    "/user" to userJson(),
                    // 探测与列表打的是同一个端点，返回空列表两边都自洽
                    "/user/starred" to "[]",
                ),
            )

            val viewModel = viewModel()

            assertTrue(viewModel.starred.asSnapshot().isEmpty())
            val paths = recordedPaths()
            assertTrue("/user" in paths)
            assertTrue("/user/starred" in paths)
        }

    private fun viewModel(): ProfileViewModel {
        val sessionManager = mockk<OAuthSessionManager>()
        every { sessionManager.authState } returns
            MutableStateFlow(
                AuthState.SignedIn(
                    SessionData(accessToken = "token"),
                ),
            )
        val retrofit = createRetrofit(server)
        return ProfileViewModel(
            savedStateHandle = SavedStateHandle(),
            profileRepository =
                ProfileRepository(
                    userApi = retrofit.create(UserApi::class.java),
                    gistApi = retrofit.create(GistApi::class.java),
                ),
            sessionManager = sessionManager,
        )
    }
}

/** 构造指向 MockWebServer 的 Retrofit（复用 core:github-rest 工厂，零真实网络） */
private fun createRetrofit(server: MockWebServer): Retrofit {
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
    return retrofit
}

private fun jsonResponse(body: String): MockResponse =
    MockResponse
        .Builder()
        .body(body)
        .addHeader("Content-Type", "application/json")
        .build()

private fun userJson(login: String = "octocat"): String =
    """
    {
      "login": "$login",
      "id": 1,
      "name": "The Octocat",
      "avatar_url": "https://avatars.githubusercontent.com/u/1",
      "html_url": "https://github.com/octocat",
      "bio": "GitHub mascot",
      "type": "User",
      "public_repos": 8,
      "followers": 9000,
      "following": 10
    }
    """.trimIndent()

private fun repositoryJson(name: String): String =
    """
    {
      "id": 1,
      "name": "$name",
      "full_name": "octocat/$name",
      "private": false,
      "owner": { "login": "octocat", "id": 1, "avatar_url": "https://a/u/1", "html_url": "https://github.com/octocat" },
      "description": "desc of $name",
      "html_url": "https://github.com/octocat/$name",
      "stargazers_count": 100,
      "forks_count": 5,
      "language": "Kotlin",
      "default_branch": "main"
    }
    """.trimIndent()
