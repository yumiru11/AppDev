package com.yumiru11.githubapp.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页截图基准（light / dark 两帧，外观分组 + 开发者分组 + 通用分组）。
 *
 * 基准 PNG：feature/settings/src/test/screenshots/SettingsScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：与其余新屏统一捕获路径（规避 Roborazzi compose
 * 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]）。
 *
 * 关键：屏幕内层 `AppTheme` 按 **主题模式** 渲染，而不是 capture 的 darkTheme 包装 ——
 * 暗色帧必须把 `themeMode` 固定成 [ThemeMode.DARK]（SYSTEM 在 Robolectric 默认是浅色，
 * 会拍出一张「暗色命名、浅色内容」的假帧）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun settingsViewModel(dark: Boolean): SettingsViewModel =
        mockk(relaxed = true) {
            every { uiState } returns
                MutableStateFlow(
                    SettingsUiState(
                        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                        dynamicColorEnabled = false,
                        oledEnabled = false,
                        highContrastEnabled = false,
                    ),
                )
        }

    @Test
    fun settingsScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "SettingsScreen_light", darkTheme = false) {
            SettingsScreen(viewModel = settingsViewModel(dark = false), onBackClick = {})
        }
    }

    @Test
    fun settingsScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "SettingsScreen_dark", darkTheme = true) {
            SettingsScreen(viewModel = settingsViewModel(dark = true), onBackClick = {})
        }
    }
}
