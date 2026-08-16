package com.yumiru11.githubapp.feature.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * ProfileViewModel 单测（纯 JVM，MockK 桩 ProfileRepository + OAuthSessionManager.authState）。
 *
 * 覆盖：未登录 → Anonymous；SignedIn/PAT → Success；错误映射（404/网络/未知）；
 * retry 重载；未登录 → 已登录状态迁移重载；USER 路由 login 参数透传。
 */
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: ProfileRepository,
        authStateFlow: MutableStateFlow<AuthState>,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): ProfileViewModel {
        val sessionManager = mockk<OAuthSessionManager>()
        every { sessionManager.authState } returns authStateFlow
        return ProfileViewModel(
            savedStateHandle = savedStateHandle,
            profileRepository = repository,
            sessionManager = sessionManager,
        )
    }

    private fun signedInState(): MutableStateFlow<AuthState> =
        MutableStateFlow(
            AuthState.SignedIn(
                SessionData(
                    accessToken = "token",
                    refreshToken = "refresh",
                ),
            ),
        )

    private fun fakeUserWithStats(): User =
        User(
            login = "octocat",
            name = "The Octocat",
            avatarUrl = "https://avatars.githubusercontent.com/u/583231?v=4",
            bio = "GitHub mascot",
            publicRepos = 8,
            followers = 9_000,
            following = 10,
        )

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    @Test
    fun init_anonymousAuth_emitsAnonymous() =
        runTest {
            val repository = mockk<ProfileRepository>()
            val authState = MutableStateFlow<AuthState>(AuthState.Anonymous)

            val state = viewModel(repository, authState).uiState.value

            assertEquals(ProfileUiState.Anonymous, state)
        }

    @Test
    fun init_signedInAuth_loadsProfileSuccess() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } returns fakeUserWithStats()
                }

            val state = viewModel(repository, signedInState()).uiState.value

            assertTrue(state is ProfileUiState.Success)
            val success = state as ProfileUiState.Success
            assertEquals("octocat", success.user.login)
            assertEquals(8, success.user.publicRepos)
            assertEquals(9_000, success.user.followers)
            assertEquals(10, success.user.following)
        }

    @Test
    fun init_patAuth_loadsProfileSuccess() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } returns GitHubFakes.fakeUser()
                }

            val state = viewModel(repository, MutableStateFlow(AuthState.PAT)).uiState.value

            assertTrue(state is ProfileUiState.Success)
        }

    @Test
    fun loadProfile_networkError_emitsErrorNetwork() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } throws IOException("network down")
                }

            val state = viewModel(repository, signedInState()).uiState.value

            assertEquals(ProfileUiState.Error(ProfileErrorType.NETWORK), state)
        }

    @Test
    fun loadProfile_notFound_emitsErrorNotFound() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = "ghost") } throws httpException(404)
                }

            val state =
                viewModel(
                    repository,
                    signedInState(),
                    savedStateHandle = SavedStateHandle(mapOf("login" to "ghost")),
                ).uiState.value

            assertEquals(ProfileUiState.Error(ProfileErrorType.NOT_FOUND), state)
        }

    @Test
    fun loadProfile_unknownError_emitsErrorUnknown() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } throws IllegalStateException("unexpected")
                }

            val state = viewModel(repository, signedInState()).uiState.value

            assertEquals(ProfileUiState.Error(ProfileErrorType.UNKNOWN), state)
        }

    @Test
    fun retry_afterError_reloadsAndSucceeds() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } throws IOException("first attempt failed")
                }
            val viewModel = viewModel(repository, signedInState())
            assertEquals(ProfileUiState.Error(ProfileErrorType.NETWORK), viewModel.uiState.value)

            coEvery { repository.getProfile(login = null) } returns GitHubFakes.fakeUser()

            viewModel.retry()

            assertTrue(viewModel.uiState.value is ProfileUiState.Success)
        }

    @Test
    fun authStateTransition_anonymousToSignedIn_reloadsProfile() =
        runTest {
            val authState = MutableStateFlow<AuthState>(AuthState.Anonymous)
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } returns GitHubFakes.fakeUser()
                }
            val viewModel = viewModel(repository, authState)
            assertEquals(ProfileUiState.Anonymous, viewModel.uiState.value)

            authState.value = AuthState.SignedIn(SessionData(accessToken = "token"))

            assertTrue(viewModel.uiState.value is ProfileUiState.Success)
        }

    @Test
    fun userRoute_loginFromSavedState_loadsThatUser() =
        runTest {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = "torvalds") } returns
                        GitHubFakes.fakeUser(login = "torvalds")
                }

            val state =
                viewModel(
                    repository,
                    signedInState(),
                    savedStateHandle = SavedStateHandle(mapOf("login" to "torvalds")),
                ).uiState.value

            val success = state as ProfileUiState.Success
            assertEquals("torvalds", success.user.login)
        }

    @Test
    fun loadProfile_delayedResponse_emitsLoadingThenSuccess() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } coAnswers {
                        delay(1_000)
                        fakeUserWithStats()
                    }
                }
            val viewModel = viewModel(repository, signedInState())
            // init 已同步执行到 delay 挂起点 → Loading 已发布（Unconfined Main）
            assertEquals(ProfileUiState.Loading, viewModel.uiState.value)

            viewModel.uiState.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                advanceTimeBy(1_000)
                val success = awaitItem() as ProfileUiState.Success
                assertEquals("octocat", success.user.login)
                assertEquals(9_000, success.user.followers)
            }
        }

    @Test
    fun retry_afterSuccess_refreshesUserData() =
        runTest {
            val repository = mockk<ProfileRepository>()
            coEvery { repository.getProfile(login = null) } returns GitHubFakes.fakeUser(login = "first")
            val viewModel = viewModel(repository, signedInState())
            assertEquals("first", (viewModel.uiState.value as ProfileUiState.Success).user.login)

            coEvery { repository.getProfile(login = null) } returns GitHubFakes.fakeUser(login = "second")
            viewModel.retry()

            assertEquals("second", (viewModel.uiState.value as ProfileUiState.Success).user.login)
            coVerify(exactly = 2) { repository.getProfile(login = null) }
        }

    @Test
    fun authStateTransition_toSignedInWithFailingRepository_emitsError() =
        runTest {
            val authState = MutableStateFlow<AuthState>(AuthState.Anonymous)
            val repository =
                mockk<ProfileRepository> {
                    coEvery { getProfile(login = null) } throws IOException("network down")
                }
            val viewModel = viewModel(repository, authState)
            assertEquals(ProfileUiState.Anonymous, viewModel.uiState.value)

            authState.value = AuthState.SignedIn(SessionData(accessToken = "token"))

            assertEquals(ProfileUiState.Error(ProfileErrorType.NETWORK), viewModel.uiState.value)
        }
}
