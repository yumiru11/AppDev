package com.yumiru11.githubapp.feature.notifications

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.notifications.data.NotificationRepository
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * NotificationsViewModel 单测（纯 JVM，MockK 桩 Repository 与 T4 OAuthSessionManager）。
 *
 * 覆盖：登录态（SignedIn/PAT/Anonymous）→ UiState、过滤切换重建数据流、
 * 单条/全部已读委托、构造期异常 → Error、retry 恢复。
 */
class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun sessionManager(auth: AuthState): OAuthSessionManager =
        mockk<OAuthSessionManager> {
            every { authState } returns MutableStateFlow(auth)
        }

    private fun repository(): NotificationRepository =
        mockk<NotificationRepository> {
            every { notifications(any()) } returns flowOf(PagingData.empty())
        }

    private fun item(id: String = "1"): NotificationItem =
        NotificationItem(
            id = id,
            repoFullName = "octocat/Hello-World",
            subjectTitle = "Greetings",
            subjectType = "Issue",
            reason = "mention",
            unread = true,
            updatedAt = "2026-08-01T10:00:00Z",
            htmlUrl = "https://github.com/octocat/Hello-World/issues/1347",
        )

    @Test
    fun load_signedIn_emitsSuccessWithDefaultFilter() =
        runTest {
            val viewModel =
                NotificationsViewModel(
                    repository(),
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            val state = viewModel.uiState.value
            assertTrue(state is NotificationsUiState.Success)
            assertEquals(NotificationFilter.ALL, (state as NotificationsUiState.Success).filter)
        }

    @Test
    fun load_patMode_emitsSuccess() =
        runTest {
            val viewModel = NotificationsViewModel(repository(), sessionManager(AuthState.PAT))

            assertTrue(viewModel.uiState.value is NotificationsUiState.Success)
        }

    @Test
    fun load_anonymous_emitsUnauthenticated() =
        runTest {
            val viewModel = NotificationsViewModel(repository(), sessionManager(AuthState.Anonymous))

            assertEquals(NotificationsUiState.Unauthenticated, viewModel.uiState.value)
        }

    @Test
    fun selectFilter_participating_reloadsWithNewFilter() =
        runTest {
            val repo = repository()
            val viewModel =
                NotificationsViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            viewModel.selectFilter(NotificationFilter.PARTICIPATING)

            assertEquals(NotificationFilter.PARTICIPATING, viewModel.filter.value)
            assertEquals(
                NotificationFilter.PARTICIPATING,
                (viewModel.uiState.value as NotificationsUiState.Success).filter,
            )
            verify(exactly = 1) { repo.notifications(NotificationFilter.ALL) }
            verify(exactly = 1) { repo.notifications(NotificationFilter.PARTICIPATING) }
        }

    @Test
    fun markRead_callsRepositoryWithThreadId() =
        runTest {
            val repo = repository()
            val viewModel =
                NotificationsViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            viewModel.markRead(item(id = "42"))

            coVerify { repo.markRead("42") }
        }

    @Test
    fun markAllRead_callsRepository() =
        runTest {
            val repo = repository()
            val viewModel =
                NotificationsViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            viewModel.markAllRead()

            coVerify { repo.markAllRead() }
        }

    @Test
    fun load_repositoryThrows_emitsErrorUnknown() =
        runTest {
            val repo =
                mockk<NotificationRepository> {
                    every { notifications(any()) } throws IllegalStateException("boom")
                }

            val viewModel =
                NotificationsViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )

            assertEquals(NotificationsUiState.Error(NotificationsErrorType.UNKNOWN), viewModel.uiState.value)
        }

    @Test
    fun retry_afterError_reloadsAndSucceeds() =
        runTest {
            val repo =
                mockk<NotificationRepository> {
                    every { notifications(any()) } throws IllegalStateException("boom")
                }
            val viewModel =
                NotificationsViewModel(
                    repo,
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )
            assertEquals(NotificationsUiState.Error(NotificationsErrorType.UNKNOWN), viewModel.uiState.value)

            every { repo.notifications(any()) } returns flowOf(PagingData.empty())
            viewModel.retry()

            assertTrue(viewModel.uiState.value is NotificationsUiState.Success)
        }
}
