package com.yumiru11.githubapp.core.ui

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AppBottomBar 截图基准测试（light / dark / 选中仓库 Tab / 玻璃关闭四态）。
 *
 * 基准 PNG：core/ui/src/test/screenshots/AppBottomBar_{light,dark,repos,blurDisabled}.png
 *
 * Robolectric 限制标注：同 AppTopBarScreenshotTest（模糊像素不可见，基准断言
 * 玻璃层装配与布局，模糊代码路径由编译与渲染不崩溃保证）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppBottomBarScreenshotTest : ScreenshotTest() {
    @Test
    fun appBottomBar_lightTheme_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_light", darkTheme = false) {
            AppBottomBar(
                selectedTab = MainTab.HOME,
                onTabSelected = {},
            )
        }
    }

    @Test
    fun appBottomBar_darkTheme_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_dark", darkTheme = true) {
            AppBottomBar(
                selectedTab = MainTab.HOME,
                onTabSelected = {},
            )
        }
    }

    @Test
    fun appBottomBar_reposTabSelected_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_repos", darkTheme = false) {
            AppBottomBar(
                selectedTab = MainTab.REPOS,
                onTabSelected = {},
            )
        }
    }

    @Test
    fun appBottomBar_blurDisabled_matchesBaseline() {
        captureScreenshot(name = "AppBottomBar_blurDisabled", darkTheme = false) {
            AppBottomBar(
                selectedTab = MainTab.HOME,
                onTabSelected = {},
                blurEnabled = false,
            )
        }
    }
}
