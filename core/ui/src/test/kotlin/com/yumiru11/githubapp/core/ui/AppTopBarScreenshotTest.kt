package com.yumiru11.githubapp.core.ui

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AppTopBar 截图基准测试（light / dark / 未读角标三态）。
 *
 * 基准 PNG：core/ui/src/test/screenshots/AppTopBar_{light,dark,unread}.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppTopBarScreenshotTest : ScreenshotTest() {
    @Test
    fun appTopBar_lightTheme_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_light", darkTheme = false) {
            AppTopBar(
                onSearchClick = {},
                onNotificationClick = {},
                onProfileClick = {},
                unreadCount = 0,
            )
        }
    }

    @Test
    fun appTopBar_darkTheme_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_dark", darkTheme = true) {
            AppTopBar(
                onSearchClick = {},
                onNotificationClick = {},
                onProfileClick = {},
                unreadCount = 0,
            )
        }
    }

    @Test
    fun appTopBar_withUnreadBadge_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_unread", darkTheme = false) {
            AppTopBar(
                onSearchClick = {},
                onNotificationClick = {},
                onProfileClick = {},
                unreadCount = 5,
            )
        }
    }
}
