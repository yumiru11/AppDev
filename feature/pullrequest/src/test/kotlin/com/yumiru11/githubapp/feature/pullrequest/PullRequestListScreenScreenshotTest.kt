package com.yumiru11.githubapp.feature.pullrequest

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
 * PR 列表页截图基准（light / dark / RTL 三帧）。
 *
 * 基准 PNG：feature/pullrequest/src/test/screenshots/PullRequestListScreen_{light,dark,rtl}.png（入库）。
 *
 * ## 为什么用 `captureScreenshotDeterministic`
 *
 * 列表内容包在 `PullToRefreshBox` 里（下拉刷新指示器是无限动画）。Roborazzi 的 compose
 * 捕获路径在截图前调用 `ShadowLooper.shadowMainLooper().idle()`，无限动画让 looper 队列
 * 永不为空 → `idle()` 永不返回 → verify/record 挂死（jstack 实证见
 * [captureScreenshotDeterministic] KDoc；:feature:issue 列表页同款根因）。改用
 * 「冻结测试时钟 + 手工 draw + Bitmap 捕获」，同一份代码两次独立运行 PNG md5 相同。
 *
 * RTL 帧在组合内显式 `LocalLayoutDirection provides Rtl` + 组合期断言——Robolectric 的
 * `@Config(qualifiers="…-ldrtl")` 只改 host configuration、不改变组合内 `LocalLayoutDirection`
 * （:feature:issue 探针实证：qualifier 下仍为 Ltr），静默失败会让截图矩阵「假绿」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PullRequestListScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullRequestListScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestListScreen_light", darkTheme = false) {
            PullRequestListScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                onPullRequestClick = { _, _, _ -> },
                viewModel = pullRequestListViewModel(),
            )
        }
    }

    @Test
    fun pullRequestListScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestListScreen_dark", darkTheme = true) {
            PullRequestListScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                onPullRequestClick = { _, _, _ -> },
                viewModel = pullRequestListViewModel(),
            )
        }
    }

    @Test
    fun pullRequestListScreen_rtlLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestListScreen_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                PullRequestListScreen(
                    owner = "octocat",
                    repo = "Hello-World",
                    onBackClick = {},
                    onPullRequestClick = { _, _, _ -> },
                    viewModel = pullRequestListViewModel(),
                )
            }
        }
    }
}
