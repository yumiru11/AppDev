package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue 列表页截图基准测试（light / dark 各一张）。
 *
 * 基准 PNG：feature/issue/src/test/screenshots/IssueListScreen_{light,dark}.png（入库）。
 * 用 MockK 桩 IssueRepository（返回固定 PagingData）构造真实 ViewModel，避免 Hilt/Robolectric 装配。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IssueListScreenScreenshotTest : ScreenshotTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(): IssueListViewModel {
        val repository =
            mockk<IssueRepository> {
                every { issues(any(), any(), any()) } returns
                    flowOf(
                        PagingData.from(
                            listOf(
                                Issue(
                                    id = 1L,
                                    number = 42,
                                    title = "Bug report: crash on startup",
                                    state = IssueState.OPEN,
                                    author = IssueUser(login = "octocat"),
                                    commentCount = 3,
                                ),
                                Issue(
                                    id = 2L,
                                    number = 7,
                                    title = "Add dark mode support",
                                    state = IssueState.CLOSED,
                                    author = IssueUser(login = "hubot"),
                                    commentCount = 0,
                                ),
                            ),
                        ),
                    )
            }
        return IssueListViewModel(
            SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
            repository,
        )
    }

    @Test
    fun issueListScreen_lightTheme_matchesBaseline() {
        captureScreenshot(name = "IssueListScreen_light", darkTheme = false) {
            IssueListScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                onIssueClick = { _, _, _, _ -> },
                viewModel = viewModel(),
            )
        }
    }

    @Test
    fun issueListScreen_darkTheme_matchesBaseline() {
        captureScreenshot(name = "IssueListScreen_dark", darkTheme = true) {
            IssueListScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                onIssueClick = { _, _, _, _ -> },
                viewModel = viewModel(),
            )
        }
    }
}
