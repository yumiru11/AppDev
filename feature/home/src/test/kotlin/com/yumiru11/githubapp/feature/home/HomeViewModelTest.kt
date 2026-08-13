package com.yumiru11.githubapp.feature.home

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.home.data.FeedRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

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
}
