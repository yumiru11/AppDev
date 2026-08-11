package com.yumiru11.githubapp.core.ui

import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AppBottomBar 截图基准测试（light / dark / 选中仓库 Tab 三态）。
 *
 * 基准 PNG：core/ui/src/test/screenshots/AppBottomBar_{light,dark,repos}.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppBottomBarScreenshotTest : ScreenshotTest() {
    @Test
    fun appBottomBar_lightTheme_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_light", darkTheme = false) {
            AppBottomBar(
                selectedTab = AppRoute.HOME,
                onTabSelected = {},
            )
        }
    }

    @Test
    fun appBottomBar_darkTheme_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_dark", darkTheme = true) {
            AppBottomBar(
                selectedTab = AppRoute.HOME,
                onTabSelected = {},
            )
        }
    }

    @Test
    fun appBottomBar_reposTabSelected_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_repos", darkTheme = false) {
            AppBottomBar(
                selectedTab = TAB_REPOS,
                onTabSelected = {},
            )
        }
    }
}
