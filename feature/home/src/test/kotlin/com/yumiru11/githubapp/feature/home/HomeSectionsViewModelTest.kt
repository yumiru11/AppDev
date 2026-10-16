package com.yumiru11.githubapp.feature.home

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.home.data.FeedRepository
import com.yumiru11.githubapp.feature.home.data.HomeSectionsRepository
import com.yumiru11.githubapp.feature.home.data.TrendRepository
import com.yumiru11.githubapp.feature.home.model.HomeDiscussionItem
import com.yumiru11.githubapp.feature.home.model.HomeIssueFilter
import com.yumiru11.githubapp.feature.home.model.HomeIssueItem
import com.yumiru11.githubapp.feature.home.model.HomePullFilter
import com.yumiru11.githubapp.feature.home.model.HomeUserItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertIs

/**
 * 首页五个内容分区的状态机单测（2026-10 UI 重构新增）。
 *
 * 覆盖：首次进入分区才加载（幂等，防止左右滑来回切分区反复打搜索配额）、子过滤切换重载、
 * 失败 → Error 态且不牵连其它分区、发现分区两个二级列表各自独立成败。
 */
class HomeSectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun warmUpMockK() {
        // 与 HomeViewModelTest 同因：把 MockK/ViewModel 首次类加载的墙钟开销移出 runTest 超时窗口
        viewModel(relaxedSections())
    }

    private fun relaxedSections(): HomeSectionsRepository =
        mockk(relaxed = true) {
            coEvery { issues(any(), any()) } returns emptyList()
            coEvery { pulls(any(), any()) } returns emptyList()
            coEvery { discussions() } returns emptyList()
            coEvery { suggestedUsers(any()) } returns emptyList()
            coEvery { hotIssues(any()) } returns emptyList()
        }

    private fun issue(number: Int) =
        HomeIssueItem(
            id = number.toLong(),
            number = number,
            title = "Issue $number",
            repoFullName = "octocat/Hello-World",
            state = "open",
            isPullRequest = false,
            isDraft = false,
            isMerged = false,
            commentCount = 0,
            updatedAt = null,
            labels = emptyList(),
        )

    private fun viewModel(sections: HomeSectionsRepository): HomeViewModel {
        val feed =
            mockk<FeedRepository> {
                coEvery { currentLogin() } returns "octocat"
                every { feed(any()) } returns flowOf(PagingData.empty())
            }
        val trend = mockk<TrendRepository> { coEvery { trending(any()) } returns emptyList() }
        val session =
            mockk<OAuthSessionManager> {
                every { authState } returns MutableStateFlow<AuthState>(AuthState.SignedIn(SessionData(accessToken = "t")))
            }
        return HomeViewModel(feed, trend, sections, session)
    }

    @Test
    fun ensureSectionStarted_issues_loadsOnceAndStaysIdempotent() =
        runTest {
            val sections = relaxedSections()
            coEvery { sections.issues(any(), any()) } returns listOf(issue(1))
            val vm = viewModel(sections)

            vm.ensureSectionStarted(HomeTab.ISSUES)
            advanceUntilIdle()
            val loaded = assertIs<HomeSectionState.Data<HomeIssueItem>>(vm.issues.value)
            assertEquals(1, loaded.items.size)

            // 第二次进入同一分区不再请求（搜索配额守卫）
            vm.ensureSectionStarted(HomeTab.ISSUES)
            advanceUntilIdle()
            coVerify(exactly = 1) { sections.issues(any(), any()) }
        }

    @Test
    fun selectIssueFilter_reloadsWithNewFilter() =
        runTest {
            val sections = relaxedSections()
            coEvery { sections.issues(any(), any()) } returns listOf(issue(2))
            val vm = viewModel(sections)

            vm.ensureSectionStarted(HomeTab.ISSUES)
            advanceUntilIdle()
            vm.selectIssueFilter(HomeIssueFilter.CREATED)
            advanceUntilIdle()

            coVerify { sections.issues(HomeIssueFilter.CREATED, any()) }
        }

    @Test
    fun selectSameIssueFilter_doesNotReload() =
        runTest {
            val sections = relaxedSections()
            val vm = viewModel(sections)

            vm.selectIssueFilter(HomeIssueFilter.ASSIGNED)
            advanceUntilIdle()
            coVerify(exactly = 0) { sections.issues(any(), any()) }
        }

    @Test
    fun issueLoadFailure_setsErrorStateWithoutTouchingPulls() =
        runTest {
            val sections = relaxedSections()
            coEvery { sections.issues(any(), any()) } throws IOException("boom")
            val vm = viewModel(sections)

            vm.ensureSectionStarted(HomeTab.ISSUES)
            advanceUntilIdle()

            assertIs<HomeSectionState.Error>(vm.issues.value)
            assertEquals(HomeSectionState.Loading, vm.pulls.value)
        }

    @Test
    fun ensureSectionStarted_pulls_loadsInvolvedByDefault() =
        runTest {
            val sections = relaxedSections()
            coEvery { sections.pulls(any(), any()) } returns listOf(issue(3))
            val vm = viewModel(sections)

            vm.ensureSectionStarted(HomeTab.PULL_REQUESTS)
            advanceUntilIdle()

            assertIs<HomeSectionState.Data<HomeIssueItem>>(vm.pulls.value)
            coVerify { sections.pulls(HomePullFilter.INVOLVED, any()) }
        }

    @Test
    fun ensureSectionStarted_discussions_loadsData() =
        runTest {
            val sections = relaxedSections()
            coEvery { sections.discussions() } returns
                listOf(
                    HomeDiscussionItem(
                        id = "D_1",
                        number = 1,
                        title = "Discussion",
                        repoFullName = "octocat/Hello-World",
                        categoryName = "Ideas",
                        isAnswered = false,
                        commentCount = 0,
                        upvotes = 3,
                        updatedAt = null,
                    ),
                )
            val vm = viewModel(sections)

            vm.ensureSectionStarted(HomeTab.DISCUSSIONS)
            advanceUntilIdle()

            assertEquals(
                1,
                assertIs<HomeSectionState.Data<HomeDiscussionItem>>(vm.discussions.value).items.size,
            )
        }

    @Test
    fun explore_partialFailure_keepsSuggestedUsersDataAndMarksHotIssuesError() =
        runTest {
            val sections = relaxedSections()
            coEvery { sections.suggestedUsers(any()) } returns
                listOf(HomeUserItem(login = "octocat", name = null, avatarUrl = null, followers = 1))
            coEvery { sections.hotIssues(any()) } throws IOException("boom")
            val vm = viewModel(sections)

            vm.retryExplore()
            advanceUntilIdle()

            assertEquals(
                1,
                assertIs<HomeSectionState.Data<HomeUserItem>>(vm.suggestedUsers.value).items.size,
            )
            assertIs<HomeSectionState.Error>(vm.hotIssues.value)
        }
}
