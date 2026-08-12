package com.yumiru11.githubapp.core.ui

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AppTopBar 截图基准测试（light / dark / 未读角标 / 玻璃关闭四态）。
 *
 * 基准 PNG：core/ui/src/test/screenshots/AppTopBar_{light,dark,unread,blurDisabled}.png
 *
 * Robolectric 限制标注（与 T6 GlassSurfaceScreenshotTest 同先例）：`Modifier.blur`
 * 依赖 RenderEffect（API 31+），Robolectric Native Graphics 渲染路径支持有限，
 * 模糊像素在基准图中不可见；blur on/off 两态基准图断言的是「半透明 surface 玻璃层
 * 装配 + 布局正确」，模糊分支的代码路径由编译与渲染不崩溃保证，真机验证见 T6 装配。
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

    @Test
    fun appTopBar_blurDisabled_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_blurDisabled", darkTheme = false) {
            AppTopBar(
                onSearchClick = {},
                onNotificationClick = {},
                onProfileClick = {},
                unreadCount = 0,
                blurEnabled = false,
            )
        }
    }
}
