package com.yumiru11.githubapp.feature.pullrequest

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PR 详情页截图基准（Conversation light/dark/rtl + Checks light + Files changed light）。
 *
 * 基准 PNG：feature/pullrequest/src/test/screenshots/
 * `PullRequestDetailScreen_{light,dark,rtl}.png`、`PullRequestDetailChecks_light.png`、
 * `PullRequestDetailFiles_light.png`（入库）。
 *
 * ## 五个区块与为什么要确定性捕获
 *
 * - Conversation：PrHeader（状态/合并性/作者/分支/标签/Reviewers/Checks 摘要）+ PR 正文
 *   （WebView）+ 时间线 + MergeBox / Review 入口。
 * - Checks：`ChecksTab` 的 CheckRun 列表（成功 + 失败详情）。
 * - Files changed：`FilesTab` 的文件列表（+N −M / 状态）——PR #240 的**文件列表缓存按
 *   head sha** 命中后返回的数据即在此区块渲染，此前该区块无任何截图覆盖。
 *
 * 详情页头部在 OPEN 态会渲染 `MergeableChip`（MERGEABLE）与 `ChecksSummaryRow`，两者在
 * pending 分支下含 `CircularProgressIndicator`（无限动画）；MergeBox 的进行中态同理。
 * 与 :feature:issue 列表页同源，走 `captureScreenshotDeterministic`（冻结组合时钟 +
 * 手工 draw decorView + `Bitmap.captureRoboImage`），避免 Roborazzi compose 捕获路径的
 * `ShadowLooper.idle()` 挂死。
 *
 * 固定尺寸包裹：正文/时间线用 `LazyColumn` + `verticalScroll`，需有界最大高度
 * （:feature:issue 详情页先例：截图探针默认约束上界无限会抛异常）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PullRequestDetailScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullRequestDetailScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestDetailScreen_light", darkTheme = false) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                PullRequestDetailScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    onBackClick = {},
                    viewModel = pullRequestDetailViewModel(),
                )
            }
        }
    }

    @Test
    fun pullRequestDetailScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestDetailScreen_dark", darkTheme = true) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                PullRequestDetailScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    onBackClick = {},
                    viewModel = pullRequestDetailViewModel(),
                )
            }
        }
    }

    @Test
    fun pullRequestDetailScreen_rtlLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestDetailScreen_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                    PullRequestDetailScreen(
                        owner = "octocat",
                        repo = "Hello-World",
                        number = 42,
                        onBackClick = {},
                        viewModel = pullRequestDetailViewModel(),
                    )
                }
            }
        }
    }

    @Test
    fun pullRequestDetailChecksTab_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestDetailChecks_light", darkTheme = false) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                PullRequestDetailScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    onBackClick = {},
                    viewModel = pullRequestDetailViewModel(tab = PullRequestTab.CHECKS),
                )
            }
        }
    }

    @Test
    fun pullRequestDetailFilesTab_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestDetailFiles_light", darkTheme = false) {
            Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                PullRequestDetailScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    number = 42,
                    onBackClick = {},
                    viewModel = pullRequestDetailViewModel(tab = PullRequestTab.FILES),
                )
            }
        }
    }
}
