package com.yumiru11.githubapp.feature.notifications

import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.notifications.data.NotificationRepository
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * NotificationsPanelViewModel 单测（纯 JVM，MockK 桩 Repository 与 OAuthSessionManager）。
 *
 * 覆盖：登录态驱动加载、分组快照排序、过滤切换重拉、折叠切换、乐观已读（单条/全部）、
 * 乐观删除（组空整组消失）与失败重拉对齐、错误分类映射（401/403 → UNAUTHORIZED 等）。
 */
class NotificationsPanelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun sessionManager(auth: AuthState): OAuthSessionManager =
        mockk {
            every { authState } returns MutableStateFlow(auth)
        }

    private fun repository(latest: List<NotificationItem> = emptyList()): NotificationRepository =
        mockk {
            coEvery { latest(any()) } returns latest
            coEvery { markRead(any()) } returns Unit
            coEvery { markAllRead() } returns Unit
            coEvery { markDone(any()) } returns Unit
        }

    private fun item(
        id: String,
        repo: String = "a/A",
        updatedAt: String = "2026-08-01T10:00:00Z",
        unread: Boolean = true,
    ): NotificationItem =
        NotificationItem(
            id = id,
            repoFullName = repo,
            subjectTitle = "title",
            subjectType = "Issue",
            reason = "subscribed",
            unread = unread,
            updatedAt = updatedAt,
            htmlUrl = null,
        )

    private fun viewModel(
        repo: NotificationRepository,
        auth: AuthState = AuthState.SignedIn(SessionData(accessToken = "tok")),
    ): NotificationsPanelViewModel = NotificationsPanelViewModel(repo, sessionManager(auth))

    @Test
    fun load_signedIn_emitsGroupsSortedByLatestDesc() =
        runTest {
            val model =
                viewModel(
                    repository(
                        listOf(
                            item("1", repo = "a/A", updatedAt = "2026-08-01T10:00:00Z"),
                            item("2", repo = "b/B", updatedAt = "2026-08-02T10:00:00Z"),
                            item("3", repo = "a/A", updatedAt = "2026-08-03T10:00:00Z"),
                        ),
                    ),
                )

            val state = model.uiState.value
            assertTrue(state is NotificationsPanelUiState.Success)
            state as NotificationsPanelUiState.Success
            assertEquals(listOf("a/A", "b/B"), state.groups.map { it.repoFullName })
            assertEquals(listOf("3", "1"), state.groups[0].items.map { it.id })
        }

    @Test
    fun load_anonymous_emitsUnauthenticated() =
        runTest {
            val model = viewModel(repository(), auth = AuthState.Anonymous)

            assertEquals(NotificationsPanelUiState.Unauthenticated, model.uiState.value)
        }

    @Test
    fun selectFilter_participating_reloadsLatestWithNewFilter() =
        runTest {
            val repo = repository(listOf(item("1")))
            val model = viewModel(repo)

            model.selectFilter(NotificationFilter.PARTICIPATING)

            assertEquals(NotificationFilter.PARTICIPATING, model.filter.value)
            coVerify(exactly = 1) { repo.latest(NotificationFilter.PARTICIPATING) }
            // 过滤切换重置折叠态
            assertTrue((model.uiState.value as NotificationsPanelUiState.Success).collapsedRepos.isEmpty())
        }

    @Test
    fun toggleGroup_expandedRepo_togglesCollapsedSet() =
        runTest {
            val model = viewModel(repository(listOf(item("1"), item("2", repo = "b/B"))))

            model.toggleGroup("a/A")
            assertEquals(setOf("a/A"), (model.uiState.value as NotificationsPanelUiState.Success).collapsedRepos)

            model.toggleGroup("a/A")
            assertEquals(emptySet<String>(), (model.uiState.value as NotificationsPanelUiState.Success).collapsedRepos)
        }

    @Test
    fun markRead_unreadItem_marksLocallyAndDelegatesRepository() =
        runTest {
            val repo = repository(listOf(item("1")))
            val model = viewModel(repo)

            model.markRead(item("1"))

            val groups = (model.uiState.value as NotificationsPanelUiState.Success).groups
            assertTrue(groups[0].items[0].let { !it.unread })
            coVerify(exactly = 1) { repo.markRead("1") }
        }

    @Test
    fun markAllRead_clearsAllUnreadFlagsLocally() =
        runTest {
            val model =
                viewModel(repository(listOf(item("1"), item("2", repo = "b/B", unread = false))))

            model.markAllRead()

            val state = model.uiState.value as NotificationsPanelUiState.Success
            assertTrue(state.groups.all { group -> group.items.none { it.unread } })
        }

    @Test
    fun delete_lastItemOfGroup_removesGroupAndDelegatesMarkDone() =
        runTest {
            val repo = repository(listOf(item("1", repo = "a/A"), item("2", repo = "b/B")))
            val model = viewModel(repo)

            model.delete(item("2"))

            val state = model.uiState.value as NotificationsPanelUiState.Success
            assertEquals(listOf("a/A"), state.groups.map { it.repoFullName })
            coVerify(exactly = 1) { repo.markDone("2") }
        }

    @Test
    fun delete_markDoneFailure_reloadsSnapshotToResync() =
        runTest {
            val repo = repository(listOf(item("1")))
            coEvery { repo.markDone(any()) } throws IOException()
            val model = viewModel(repo)

            model.delete(item("1"))

            // 失败 → 重拉快照，条目恢复展示（视觉回滚）
            val state = model.uiState.value as NotificationsPanelUiState.Success
            assertEquals(
                listOf("1"),
                state.groups
                    .single()
                    .items
                    .map { it.id },
            )
            coVerify(exactly = 2) { repo.latest(any()) }
        }

    @Test
    fun load_snapshotError_mapsToErrorState() =
        runTest {
            val repo =
                mockk<NotificationRepository> {
                    coEvery { latest(any()) } throws IOException()
                }

            val model = viewModel(repo)

            val state = model.uiState.value
            assertTrue(state is NotificationsPanelUiState.Error)
            assertEquals(NotificationsErrorType.NETWORK, (state as NotificationsPanelUiState.Error).errorType)
        }

    @Test
    fun toNotificationsErrorType_http401_mapsToUnauthorized() {
        val exception =
            HttpException(
                retrofit2.Response.error<Any>(
                    401,
                    okhttp3.ResponseBody.create(null, ""),
                ),
            )

        assertEquals(NotificationsErrorType.UNAUTHORIZED, exception.toNotificationsErrorType())
    }

    @Test
    fun toNotificationsErrorType_http500_mapsToNetwork() {
        val exception =
            HttpException(
                retrofit2.Response.error<Any>(
                    500,
                    okhttp3.ResponseBody.create(null, ""),
                ),
            )

        assertEquals(NotificationsErrorType.NETWORK, exception.toNotificationsErrorType())
    }

    @Test
    fun toNotificationsErrorType_illegalState_mapsToUnknown() {
        assertEquals(NotificationsErrorType.UNKNOWN, IllegalStateException().toNotificationsErrorType())
    }
}
