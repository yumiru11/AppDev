package com.yumiru11.githubapp.feature.home

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.home.data.FeedRepository
import com.yumiru11.githubapp.feature.home.model.FeedEventType
import com.yumiru11.githubapp.feature.home.model.FeedItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertIs

/**
 * HomeViewModel 单测（纯 JVM，MockK 桩 Repository 与 T4 OAuthSessionManager）。
 *
 * 覆盖：登录态（SignedIn/PAT/Anonymous）→ UiState、login 获取失败 → Error、retry 恢复。
 */
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun sessionManager(auth: AuthState): OAuthSessionManager =
        mockk<OAuthSessionManager> {
            every { authState } returns MutableStateFlow(auth)
        }

    private fun repository(): FeedRepository =
        mockk<FeedRepository> {
            coEvery { currentLogin() } returns "octocat"
            every { feed(any()) } returns flowOf(PagingData.empty())
        }

    @Test
    fun load_signedIn_emitsSuccess() =
        runTest {
            val viewModel =
                HomeViewModel(
                    repository(),
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            assertTrue(viewModel.uiState.value is HomeUiState.Success)
        }

    @Test
    fun load_patMode_emitsSuccess() =
        runTest {
            val viewModel = HomeViewModel(repository(), sessionManager(AuthState.PAT))

            assertTrue(viewModel.uiState.value is HomeUiState.Success)
        }

    @Test
    fun load_anonymous_emitsUnauthenticated() =
        runTest {
            val viewModel = HomeViewModel(repository(), sessionManager(AuthState.Anonymous))

            assertEquals(HomeUiState.Unauthenticated, viewModel.uiState.value)
        }

    @Test
    fun load_loginFetchThrowsNetworkError_emitsErrorNetwork() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } throws IOException("network down")
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            assertEquals(HomeUiState.Error(HomeErrorType.NETWORK), viewModel.uiState.value)
        }

    @Test
    fun load_loginFetchThrowsUnknownError_emitsErrorUnknown() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } throws IllegalStateException("boom")
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            assertEquals(HomeUiState.Error(HomeErrorType.UNKNOWN), viewModel.uiState.value)
        }

    @Test
    fun retry_afterError_reloadsAndSucceeds() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } throws IOException("network down")
                }
            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )
            assertEquals(HomeUiState.Error(HomeErrorType.NETWORK), viewModel.uiState.value)

            coEvery { repo.currentLogin() } returns "octocat"
            every { repo.feed(any()) } returns flowOf(PagingData.empty())
            viewModel.retry()

            assertTrue(viewModel.uiState.value is HomeUiState.Success)
        }

    @Test
    fun retry_whenNotError_doesNotReload() =
        runTest {
            val repo = repository()
            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            viewModel.retry()

            // 初始加载已调用一次 currentLogin；retry 在非 Error 态不重复加载
            io.mockk.coVerify(exactly = 1) { repo.currentLogin() }
        }

    @Test
    fun load_signedIn_showsLoadingWhileFetchInFlight() =
        runTest {
            val gate = CompletableDeferred<String>()
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } coAnswers { gate.await() }
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            // login 获取挂起期间必须停留在 Loading（不允许提前渲染空列表）
            assertEquals(HomeUiState.Loading, viewModel.uiState.value)

            every { repo.feed(any()) } returns flowOf(PagingData.empty())
            gate.complete("octocat")

            assertTrue(viewModel.uiState.value is HomeUiState.Success)
        }

    @Test
    fun load_currentLoginCancelled_staysLoadingWithoutError() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } throws CancellationException("cancelled")
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            // 取消异常原样上抛：协程取消而非失败，不得进入 Error 态
            assertEquals(HomeUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun load_feedConstructionThrowsNetworkError_emitsErrorNetwork() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } returns "octocat"
                    every { feed(any()) } throws IOException("network down")
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            // login 成功但数据流构造失败 → 同样进入 Error 态
            assertEquals(HomeUiState.Error(HomeErrorType.NETWORK), viewModel.uiState.value)
        }

    @Test
    fun load_feedConstructionThrowsUnknownError_emitsErrorUnknown() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } returns "octocat"
                    every { feed(any()) } throws IllegalStateException("boom")
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            assertEquals(HomeUiState.Error(HomeErrorType.UNKNOWN), viewModel.uiState.value)
        }

    @Test
    fun load_emptyFeed_emitsSuccess() =
        runTest {
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } returns "octocat"
                    every { feed(any()) } returns flowOf(PagingData.empty())
                }
            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            // 空 feed 仍是 Success（Empty 渲染由 UI 层基于 PagingData 判定，不进 VM 状态机）
            assertIs<HomeUiState.Success>(viewModel.uiState.value)
            io.mockk.verify { repo.feed("octocat") }
            // 空分页数据流已挂载且可被收集（避免死锁设超时）
            withTimeout(5_000) { (viewModel.uiState.value as HomeUiState.Success).feed.first() }
        }

    @Test
    fun load_nonEmptyFeed_passesFeedFlowThrough() =
        runTest {
            val item =
                FeedItem(
                    id = "1",
                    type = FeedEventType.ISSUE,
                    actorLogin = "octocat",
                    actorAvatarUrl = null,
                    repoFullName = "octocat/Hello-World",
                    action = "opened",
                    title = "Bug report",
                    number = 42,
                    commitCount = null,
                    createdAt = "2026-08-01T10:00:00Z",
                    htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
                )
            val repo =
                mockk<FeedRepository> {
                    coEvery { currentLogin() } returns "octocat"
                    every { feed(any()) } returns flowOf(PagingData.from(listOf(item)))
                }

            val viewModel =
                HomeViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            assertIs<HomeUiState.Success>(viewModel.uiState.value)
            io.mockk.verify { repo.feed("octocat") }
        }

    @Test
    fun authState_signedInToAnonymousAtRuntime_emitsUnauthenticated() =
        runTest {
            val authFlow =
                MutableStateFlow<AuthState>(AuthState.SignedIn(SessionData(accessToken = "tok")))
            val session =
                mockk<OAuthSessionManager> {
                    every { authState } returns authFlow
                }
            val viewModel = HomeViewModel(repository(), session)
            assertTrue(viewModel.uiState.value is HomeUiState.Success)

            // 运行期登出（token 失效/用户退出）→ 立即回到未登录态
            authFlow.value = AuthState.Anonymous

            assertEquals(HomeUiState.Unauthenticated, viewModel.uiState.value)
        }

    @Test
    fun authState_anonymousToSignedInAtRuntime_loadsFeed() =
        runTest {
            val authFlow = MutableStateFlow<AuthState>(AuthState.Anonymous)
            val session =
                mockk<OAuthSessionManager> {
                    every { authState } returns authFlow
                }
            val viewModel = HomeViewModel(repository(), session)
            assertEquals(HomeUiState.Unauthenticated, viewModel.uiState.value)

            // 运行期登录 → 自动加载动态流
            authFlow.value = AuthState.SignedIn(SessionData(accessToken = "tok"))

            assertTrue(viewModel.uiState.value is HomeUiState.Success)
        }

    @Test
    fun retry_whenUnauthenticated_doesNotLoad() =
        runTest {
            val repo = repository()
            val viewModel = HomeViewModel(repo, sessionManager(AuthState.Anonymous))
            assertEquals(HomeUiState.Unauthenticated, viewModel.uiState.value)

            viewModel.retry()

            // 未登录态无内容可重试，不得触发任何请求
            io.mockk.coVerify(exactly = 0) { repo.currentLogin() }
        }
}
