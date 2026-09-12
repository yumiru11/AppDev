package com.yumiru11.githubapp.feature.profile

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
 * 个人主页截图基准（light / dark / RTL 三帧，本人主页 + Repos Tab）。
 *
 * 基准 PNG：feature/profile/src/test/screenshots/ProfileScreen_{light,dark,rtl}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：四 Tab 均为 Paging 列表（含下拉刷新指示器，无限
 * 动画），Roborazzi 的 compose 捕获路径 `idle()` 永不返回（根因见
 * [captureScreenshotDeterministic]）。
 *
 * RTL 帧在组合内显式 `LocalLayoutDirection provides Rtl` + 组合期断言——Robolectric 的
 * `@Config(qualifiers="…-ldrtl")` 只改 host configuration、不改变组合内 `LocalLayoutDirection`
 * （:feature:issue / :feature:pullrequest 探针实证），静默失败会让截图矩阵「假绿」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ProfileScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profileScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ProfileScreen_light", darkTheme = false) {
            ProfileScreen(
                bottomContentPadding = 80.dp,
                viewModel = profileScreenshotViewModel(),
            )
        }
    }

    @Test
    fun profileScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ProfileScreen_dark", darkTheme = true) {
            ProfileScreen(
                bottomContentPadding = 80.dp,
                viewModel = profileScreenshotViewModel(),
            )
        }
    }

    @Test
    fun profileScreen_rtlLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ProfileScreen_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                ProfileScreen(
                    bottomContentPadding = 80.dp,
                    viewModel = profileScreenshotViewModel(),
                )
            }
        }
    }
}
