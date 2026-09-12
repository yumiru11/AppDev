package com.yumiru11.githubapp.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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
 * 首页截图基准（light / dark / RTL 三帧）。
 *
 * 基准 PNG：feature/home/src/test/screenshots/HomeScreen_{light,dark,rtl}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * ## 为什么用 `captureScreenshotDeterministic`
 *
 * 动态分区包在 `PullToRefreshBox` 里（刷新指示器是无限动画）。Roborazzi 的 compose
 * 捕获路径截图前调用 `ShadowLooper.shadowMainLooper().idle()`，无限动画让 looper 队列
 * 永不为空 → idle() 永不返回 → verify/record 挂死（根因与最小复现见
 * [captureScreenshotDeterministic] KDoc / ci.yml D02 注记）。改用「冻结测试时钟 +
 * 固定 advanceTimeBy + 手工 draw + Bitmap 捕获」，同代码两次独立运行 PNG md5 相同。
 *
 * RTL 帧在组合内显式 `LocalLayoutDirection provides Rtl` + 组合期断言——Robolectric 的
 * `@Config(qualifiers="…-ldrtl")` 只改 host configuration、不改变组合内 `LocalLayoutDirection`
 * （:feature:issue / :feature:pullrequest 探针实证），静默失败会让截图矩阵「假绿」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "HomeScreen_light", darkTheme = false) {
            HomeScreen(
                onSearchClick = {},
                onNotificationClick = {},
                onProfileClick = {},
                bottomContentPadding = 80.dp,
                viewModel = homeScreenshotViewModel(),
                pickerViewModel = homePickerScreenshotViewModel(),
            )
        }
    }

    @Test
    fun homeScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "HomeScreen_dark", darkTheme = true) {
            HomeScreen(
                onSearchClick = {},
                onNotificationClick = {},
                onProfileClick = {},
                bottomContentPadding = 80.dp,
                viewModel = homeScreenshotViewModel(),
                pickerViewModel = homePickerScreenshotViewModel(),
            )
        }
    }

    @Test
    fun homeScreen_rtlLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "HomeScreen_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                HomeScreen(
                    onSearchClick = {},
                    onNotificationClick = {},
                    onProfileClick = {},
                    bottomContentPadding = 80.dp,
                    viewModel = homeScreenshotViewModel(),
                    pickerViewModel = homePickerScreenshotViewModel(),
                )
            }
        }
    }
}
