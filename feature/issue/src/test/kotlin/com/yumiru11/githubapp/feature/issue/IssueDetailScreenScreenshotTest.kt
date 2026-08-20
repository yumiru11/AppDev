package com.yumiru11.githubapp.feature.issue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue 详情页截图基准测试（light / dark 各一张）。
 *
 * 基准 PNG：feature/issue/src/test/screenshots/IssueDetailScreen_{light,dark}.png（入库）。
 * 用 MockK 桩 IssueRepository（getIssue/timeline）构造真实 ViewModel，避免 Hilt/Robolectric 装配；
 * 正文/评论用无代码块 Markdown，规避 TextMate 高亮路径。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IssueDetailScreenScreenshotTest : ScreenshotTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(): IssueDetailViewModel {
        val repository =
            mockk<IssueRepository> {
                coEvery { getIssue("octocat", "Hello-World", 42) } returns
                    Issue(
                        id = 1L,
                        number = 42,
                        title = "Bug report: crash on startup",
                        state = IssueState.OPEN,
                        body = "This is a **bug** description with a [link](https://github.com).",
                        author = IssueUser(login = "octocat"),
                        labels = listOf(IssueLabel(name = "bug", color = "d73a4a")),
                        assignees = listOf(IssueUser(login = "octocat"), IssueUser(login = "hubot")),
                        createdAt = "2026-01-01T10:00:00Z",
                        htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
                    )
                coEvery { timeline("octocat", "Hello-World", 42) } returns
                    listOf(
                        IssueTimelineItem.Comment(
                            id = 10L,
                            author = IssueUser(login = "hubot"),
                            body = "Looks good to me, thanks!",
                            createdAt = "2026-01-01T10:00:00Z",
                        ),
                        IssueTimelineItem.Event(
                            id = 11L,
                            type = IssueTimelineEventType.LABELED,
                            actor = IssueUser(login = "octocat"),
                            label = IssueLabel(name = "bug", color = "d73a4a"),
                        ),
                    )
                // T14：写操作上下文（作者本人 + WRITE 权限 → 展示写操作 UI）
                coEvery { getIssueWriteContext("octocat", "Hello-World", 42) } returns
                    IssueWriteContext(
                        viewerLogin = "octocat",
                        viewerPermission = IssueViewerPermission.WRITE,
                        issueNodeId = "I_kwDOA",
                    )
            }
        return IssueDetailViewModel(
            SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "number" to 42)),
            repository,
        )
    }

    @Test
    fun issueDetailScreen_lightTheme_matchesBaseline() {
        captureScreenshot(name = "IssueDetailScreen_light", darkTheme = false) {
            // 固定尺寸包裹：MarkdownViewer 的 verticalScroll 在 LazyColumn 内需有界最大高度
            // （截图探针默认约束上界为无限，否则 checkScrollableContainerConstraints 抛异常）
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                IssueDetailScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    onBackClick = {},
                    onInternalLink = {},
                    viewModel = viewModel(),
                )
            }
        }
    }

    @Test
    fun issueDetailScreen_darkTheme_matchesBaseline() {
        captureScreenshot(name = "IssueDetailScreen_dark", darkTheme = true) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                IssueDetailScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    onBackClick = {},
                    onInternalLink = {},
                    viewModel = viewModel(),
                )
            }
        }
    }
}
