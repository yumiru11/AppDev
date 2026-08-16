package com.yumiru11.githubapp.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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

    @Test
    fun repositoriesFlow_collect_loadsFirstPageAndRequestsParams() =
        runBlocking {
            val repos = (1..30).joinToString(",") { repositoryJson("repo-$it") }
            server.enqueue(jsonResponse(userJson()))
            server.enqueue(jsonResponse("[$repos]"))
            // 满页会触发 Pager 预取下一页 → 补一个空页终止
            server.enqueue(jsonResponse("[]"))

            val viewModel = viewModel()

            val items = viewModel.repositories.asSnapshot()

            assertEquals(30, items.size)
            assertEquals("repo-1", items.first().name)
            // 请求顺序：资料头先于列表（VM init 同步触发 getProfile）
            assertEquals("/user", server.takeRequest().url.encodedPath)
            val listRequest = server.takeRequest()
            assertEquals("/user/repos", listRequest.url.encodedPath)
            assertEquals("1", listRequest.url.queryParameter("page"))
            assertEquals("30", listRequest.url.queryParameter("per_page"))
        }

    @Test
    fun followersFlow_collect_loadsFirstPageUsers() =
        runBlocking {
            val users = (1..30).joinToString(",") { userJson("follower-$it") }
            server.enqueue(jsonResponse(userJson()))
            server.enqueue(jsonResponse("[$users]"))
            server.enqueue(jsonResponse("[]"))

            val viewModel = viewModel()

            val items = viewModel.followers.asSnapshot()

            assertEquals(30, items.size)
            assertEquals("follower-1", items.first().login)
            assertEquals("/user", server.takeRequest().url.encodedPath)
            assertEquals("/user/followers", server.takeRequest().url.encodedPath)
        }

    @Test
    fun starredFlow_collectEmptyList_returnsEmptySnapshot() =
        runBlocking {
            server.enqueue(jsonResponse(userJson()))
            server.enqueue(jsonResponse("[]"))

            val viewModel = viewModel()

            assertTrue(viewModel.starred.asSnapshot().isEmpty())
            assertEquals("/user", server.takeRequest().url.encodedPath)
            assertEquals("/user/starred", server.takeRequest().url.encodedPath)
        }

    private fun viewModel(): ProfileViewModel {
        val sessionManager = mockk<OAuthSessionManager>()
        every { sessionManager.authState } returns
            MutableStateFlow(
                AuthState.SignedIn(
                    SessionData(accessToken = "token"),
                ),
            )
        return ProfileViewModel(
            savedStateHandle = SavedStateHandle(),
            profileRepository = ProfileRepository(userApi = createApi(server)),
            sessionManager = sessionManager,
        )
    }
}

/** 构造指向 MockWebServer 的 UserApi（复用 core:github-rest 工厂，零真实网络） */
private fun createApi(server: MockWebServer): UserApi {
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
    return retrofit.create(UserApi::class.java)
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
