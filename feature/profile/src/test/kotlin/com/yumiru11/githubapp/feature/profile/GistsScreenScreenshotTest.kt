package com.yumiru11.githubapp.feature.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gists 列表页截图基准（light / dark 两帧，三条 gist 列表形态）。
 *
 * 基准 PNG：feature/profile/src/test/screenshots/GistsScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：列表是 Paging（加载态/下拉刷新含无限动画），
 * Roborazzi 的 compose 捕获路径 `idle()` 对无限动画永不返回（根因见
 * [captureScreenshotDeterministic]）。ViewModel 用 mockk 注入固定三条 gist。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class GistsScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gistsScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "GistsScreen_light", darkTheme = false) {
            GistsScreen(
                onBackClick = {},
                onOpenExternal = {},
                viewModel = gistsScreenshotViewModel(),
            )
        }
    }

    @Test
    fun gistsScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "GistsScreen_dark", darkTheme = true) {
            GistsScreen(
                onBackClick = {},
                onOpenExternal = {},
                viewModel = gistsScreenshotViewModel(),
            )
        }
    }
}
