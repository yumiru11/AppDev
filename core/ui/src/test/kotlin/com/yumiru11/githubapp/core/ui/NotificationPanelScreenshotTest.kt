package com.yumiru11.githubapp.core.ui

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NotificationPanelContent 截图基准测试（light / dark 两态）。
 *
 * 测试 NotificationPanelContent（无动画包装），确保面板视觉设计正确。
 * 基准 PNG：core/ui/src/test/screenshots/NotificationPanel_{light,dark}.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NotificationPanelScreenshotTest : ScreenshotTest() {
    @Test
    fun notificationPanel_lightTheme_matchesBaseline() {
        captureScreenshot(name = "NotificationPanel_light", darkTheme = false) {
            NotificationPanelContent(
                onDismiss = {},
                onMarkAllRead = {},
            )
        }
    }

    @Test
    fun notificationPanel_darkTheme_matchesBaseline() {
        captureScreenshot(name = "NotificationPanel_dark", darkTheme = true) {
            NotificationPanelContent(
                onDismiss = {},
                onMarkAllRead = {},
            )
        }
    }
}
