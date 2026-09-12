package com.yumiru11.githubapp.feature.issue

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue 列表页截图基准（light / dark / RTL 三帧）。
 *
 * 基准 PNG：feature/issue/src/test/screenshots/IssueListScreen_{light,dark,rtl}.png（入库）。
 *
 * ## 为什么不用 `ScreenshotTest.captureScreenshot`
 *
 * 本屏幕的 Paging 首帧会渲染 `PullToRefreshBox` 刷新指示器（无限动画）。Roborazzi 的
 * compose 捕获路径在截图前调用 `ShadowLooper.shadowMainLooper().idle()`，无限动画让
 * looper 队列永不为空 → `idle()` 永不返回 → `verifyRoborazziDebug` 挂死（jstack 实证见
 * [captureScreenshotDeterministic] KDoc）。改用「冻结测试时钟 + 手工 draw + Bitmap 捕获」，
 * 同一份代码两次独立运行 PNG md5 相同（确定性）。
 *
 * RTL 帧在组合内显式 `LocalLayoutDirection provides Rtl`，并加组合期断言——
 * Robolectric 的 `@Config(qualifiers="…-ldrtl")` 只改 host configuration、不改变组合内
 * `LocalLayoutDirection`（探针实证：qualifier 下仍为 Ltr），静默失败会让截图矩阵「假绿」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IssueListScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun issueListScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "IssueListScreen_light", darkTheme = false) {
            IssueListScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                onIssueClick = { _, _, _, _ -> },
                viewModel = issueListViewModel(),
            )
        }
    }

    @Test
    fun issueListScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "IssueListScreen_dark", darkTheme = true) {
            IssueListScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                onIssueClick = { _, _, _, _ -> },
                viewModel = issueListViewModel(),
            )
        }
    }

    @Test
    fun issueListScreen_rtlLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "IssueListScreen_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                IssueListScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    onBackClick = {},
                    onIssueClick = { _, _, _, _ -> },
                    viewModel = issueListViewModel(),
                )
            }
        }
    }
}
