package com.yumiru11.githubapp.auth

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首个 Roborazzi 截图基准测试：游客模式登录引导占位页（light / dark 两态各一张）。
 *
 * 基准 PNG：app/src/test/screenshots/GuestWelcomeScreen_{light,dark}.png（入库）。
 * 基建（基类/Robolectric/Roborazzi 依赖）来自 :core:testing。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GuestWelcomeScreenScreenshotTest : ScreenshotTest() {
    @Test
    fun guestWelcomeScreen_lightTheme_matchesBaseline() {
        captureScreenshot(name = "GuestWelcomeScreen_light", darkTheme = false) {
            GuestWelcomeScreen()
        }
    }

    @Test
    fun guestWelcomeScreen_darkTheme_matchesBaseline() {
        captureScreenshot(name = "GuestWelcomeScreen_dark", darkTheme = true) {
            GuestWelcomeScreen()
        }
    }
}
