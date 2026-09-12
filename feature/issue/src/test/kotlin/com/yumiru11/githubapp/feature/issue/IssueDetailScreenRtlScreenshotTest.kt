package com.yumiru11.githubapp.feature.issue

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue 详情页 **全屏 RTL** 截图基准（列表页 RTL 见 [IssueListScreenScreenshotTest]）。
 *
 * 基准 PNG：feature/issue/src/test/screenshots/IssueDetailScreen_rtl.png（入库）。
 *
 * 与 light/dark 帧（[IssueDetailScreenScreenshotTest]，Roborazzi compose 路径）分开成类：
 * compose rule 的 Activity 会成为第二个 window root，若与 Roborazzi compose 捕获同类混用，
 * `captureScreenIfMultipleWindows` 会走多窗口 `captureScreenRoboImage`（Espresso onIdle）分支，
 * 帧内容与稳定性都不可控。RTL 帧统一走冻结时钟的确定性捕获（见
 * [captureScreenshotDeterministic] KDoc：为何 Roborazzi compose 路径在无限动画下挂死）。
 *
 * 方向守卫：组合内断言 `LocalLayoutDirection.current == Rtl`，防止注入静默失效时「假绿」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IssueDetailScreenRtlScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun issueDetailScreen_rtlLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "IssueDetailScreen_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                // 固定尺寸包裹：MarkdownViewer 的 verticalScroll 在 LazyColumn 内需有界最大高度
                // （同 IssueDetailScreenScreenshotTest；截图探针默认约束上界无限会抛异常）
                Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                    IssueDetailScreen(
                        owner = "octocat",
                        repo = "Hello-World",
                        number = 42,
                        onBackClick = {},
                        onInternalLink = {},
                        viewModel = issueDetailViewModel(),
                    )
                }
            }
        }
    }
}
