package com.yumiru11.githubapp.feature.auth

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T4 登录页截图基准测试（light / dark 两态各一张）。
 *
 * 基准 PNG：feature/auth/src/test/screenshots/LoginScreen_{light,dark}.png（入库）。
 * 基建（基类/Robolectric/Roborazzi 依赖）来自 :core:testing。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LoginScreenScreenshotTest : ScreenshotTest() {
    @Test
    fun loginScreen_lightTheme_matchesBaseline() {
        captureScreenshot(name = "LoginScreen_light", darkTheme = false) {
            LoginScreen()
        }
    }

    @Test
    fun loginScreen_darkTheme_matchesBaseline() {
        captureScreenshot(name = "LoginScreen_dark", darkTheme = true) {
            LoginScreen()
        }
    }
}
