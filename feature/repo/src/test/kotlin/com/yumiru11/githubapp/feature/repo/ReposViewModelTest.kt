@file:Suppress("UnusedFlow") // Turbine test{} 块内的挂起调用在静态分析里看不出被消费

package com.yumiru11.githubapp.feature.repo

import app.cash.turbine.test
import com.yumiru11.githubapp.core.datastore.model.RepoLayoutMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubdata.paging.ViewerRepositoriesPagingSource
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import javax.inject.Provider

/**
 * 「仓库」大分区 ViewModel 测试（#166 / UI01+UI02）。
 *
 * 覆盖：布局翻转的持久化语义、游客态判定、长按菜单的收藏切换（成功/失败事件）。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class ReposViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val layoutFlow = MutableStateFlow(RepoLayoutMode.LIST)

    // 非 Anonymous 即视为已登录；不用 SignedIn(session) 是为了避免构造无关的 SessionData
    private val authFlow = MutableStateFlow<AuthState>(AuthState.PAT)

    private fun preferences(): UserPreferencesRepository =
        mockk(relaxed = true) {
            every { repoLayout } returns layoutFlow
        }

    private fun sessionManager(): OAuthSessionManager =
        mockk(relaxed = true) {
            every { authState } returns authFlow
        }

    private fun viewModel(
        preferences: UserPreferencesRepository = preferences(),
        management: RepoManagementRepository = mockk(relaxed = true),
    ): ReposViewModel =
        ReposViewModel(
            preferences = preferences,
            managementRepository = management,
            sessionManager = sessionManager(),
            pagingSourceProvider = Provider { mockk<ViewerRepositoriesPagingSource>(relaxed = true) },
        )

    // ── 布局切换（UI01）─────────────────────────────────────────────────────

    @Test
    fun toggleLayout_currentlyList_persistsGrid() =
        runTest {
            val prefs = preferences()
            val viewModel = viewModel(preferences = prefs)

            viewModel.layout.test {
                awaitItem()
                viewModel.toggleLayout()
                coVerify { prefs.setRepoLayout(RepoLayoutMode.GRID) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleLayout_currentlyGrid_persistsList() =
        runTest {
            layoutFlow.value = RepoLayoutMode.GRID
            val prefs = preferences()
            val viewModel = viewModel(preferences = prefs)

            viewModel.layout.test {
                awaitItem()
                viewModel.toggleLayout()
                coVerify { prefs.setRepoLayout(RepoLayoutMode.LIST) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun layout_preferenceEmitsGrid_reflectsInState() =
        runTest {
            val viewModel = viewModel()

            viewModel.layout.test {
                assertEquals(RepoLayoutMode.LIST, awaitItem())
                layoutFlow.value = RepoLayoutMode.GRID
                assertEquals(RepoLayoutMode.GRID, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 游客态 ──────────────────────────────────────────────────────────────

    @Test
    fun isAnonymous_signedIn_emitsFalse() =
        runTest {
            val viewModel = viewModel()

            viewModel.isAnonymous.test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun isAnonymous_anonymous_emitsTrue() =
        runTest {
            authFlow.value = AuthState.Anonymous
            val viewModel = viewModel()

            viewModel.isAnonymous.test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 长按菜单：收藏切换（UI02）──────────────────────────────────────────

    @Test
    fun toggleStar_notStarred_starsAndEmitsAdded() =
        runTest {
            val management = mockk<RepoManagementRepository>(relaxed = true)
            coEvery { management.isStarred("octocat", "Hello-World") } returns false
            coEvery { management.setStarred("octocat", "Hello-World", true) } just Runs
            val viewModel = viewModel(management = management)

            viewModel.events.test {
                viewModel.toggleStar("octocat", "Hello-World")
                assertEquals(ReposEvent.StarToggled("octocat/Hello-World", starred = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleStar_alreadyStarred_unstarsAndEmitsRemoved() =
        runTest {
            val management = mockk<RepoManagementRepository>(relaxed = true)
            coEvery { management.isStarred("octocat", "Hello-World") } returns true
            coEvery { management.setStarred("octocat", "Hello-World", false) } just Runs
            val viewModel = viewModel(management = management)

            viewModel.events.test {
                viewModel.toggleStar("octocat", "Hello-World")
                assertEquals(ReposEvent.StarToggled("octocat/Hello-World", starred = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleStar_networkFailure_emitsFailedAndSkipsWrite() =
        runTest {
            val management = mockk<RepoManagementRepository>(relaxed = true)
            coEvery { management.isStarred(any(), any()) } throws IOException("offline")
            val viewModel = viewModel(management = management)

            viewModel.events.test {
                viewModel.toggleStar("octocat", "Hello-World")
                assertEquals(ReposEvent.StarFailed, awaitItem())
                coVerify(exactly = 0) { management.setStarred(any(), any(), any()) }
                cancelAndIgnoreRemainingEvents()
            }
        }
}
