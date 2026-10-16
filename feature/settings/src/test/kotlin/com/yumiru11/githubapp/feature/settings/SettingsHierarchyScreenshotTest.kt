package com.yumiru11.githubapp.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
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
 * 设置分层结构截图基线（L1 hub / L2 偏好设置 / L3 外观 / L3 界面与动效）。
 *
 * ## 为什么必须显式包 [AppTheme]
 *
 * 设置页本身**不再自带内层 AppTheme**（主题由 app 层 AppThemeHost 提供，页面只消费）。
 * 截图测试里没有 AppThemeHost，若不再手动包一层，页面会退化成 **M3 出厂默认主题**
 * （primary = #6750A4 紫）——出过这个假帧，故此处按「主题模式」显式渲染：
 * 帧名里的 default / dynamic 后缀就代表该帧用的主题，别让帧名和内容不符。
 *
 * 帧：`SettingsHierarchy_<page>_<default|dynamic>_<light|dark>`。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsHierarchyScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(dark: Boolean): SettingsViewModel {
        val preferences =
            mockk<com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository>(relaxed = true)
        every { preferences.themeMode } returns
            MutableStateFlow(
                if (dark) {
                    ThemeMode.DARK
                } else {
                    ThemeMode.LIGHT
                },
            )
        val tokenStorage = mockk<com.yumiru11.githubapp.core.githubauth.token.TokenStorage>(relaxed = true)
        val sessionManager = mockk<com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager>(relaxed = true)
        every { sessionManager.authState } returns
            MutableStateFlow(com.yumiru11.githubapp.core.githubauth.auth.AuthState.Anonymous)
        val rateLimitStore = mockk<com.yumiru11.githubapp.core.githubrest.http.RateLimitStore>(relaxed = true)
        every { rateLimitStore.snapshot } returns MutableStateFlow(null)
        return SettingsViewModel(preferences, tokenStorage, sessionManager, rateLimitStore)
    }

    // ── 默认主题（本项目调色板：暗色 = GitHub 蓝 #79C0FF）────────────────────
    @Test
    fun settingsHub_defaultDark_matchesBaseline() = capture("hub", "default", ThemeMode.DARK, dark = true)

    @Test
    fun settingsHub_defaultLight_matchesBaseline() = capture("hub", "default", ThemeMode.LIGHT, dark = false)

    @Test
    fun settingsPreferences_defaultDark_matchesBaseline() = capture("preferences", "default", ThemeMode.DARK, dark = true)

    @Test
    fun settingsAppearance_defaultDark_matchesBaseline() = capture("appearance", "default", ThemeMode.DARK, dark = true)

    @Test
    fun settingsInterface_defaultDark_matchesBaseline() = capture("interface", "default", ThemeMode.DARK, dark = true)

    // ── 动态取色（壁纸取色；Robolectric 下取 AOSP 动态方案）─────────────────
    @Test
    fun settingsHub_dynamicDark_matchesBaseline() = capture("hub", "dynamic", ThemeMode.DYNAMIC_DARK, dark = true)

    @Test
    fun settingsHub_dynamicLight_matchesBaseline() = capture("hub", "dynamic", ThemeMode.DYNAMIC_LIGHT, dark = false)

    @Test
    fun settingsInterface_dynamicDark_matchesBaseline() = capture("interface", "dynamic", ThemeMode.DYNAMIC_DARK, dark = true)

    @Test
    fun settingsAppearance_dynamicLight_matchesBaseline() = capture("appearance", "dynamic", ThemeMode.DYNAMIC_LIGHT, dark = false)

    // ── 自定义 seed（紫）——用来对比「动态取色 vs seed 色板」───────────────────
    @Test
    fun settingsHub_seedPurpleDark_matchesBaseline() =
        capture(
            page = "hub",
            variant = "seed_purple",
            mode = ThemeMode.DARK,
            dark = true,
            seed = Color(0xFF7C3AED),
        )

    private fun capture(
        page: String,
        variant: String,
        mode: ThemeMode,
        dark: Boolean,
        seed: Color? = null,
    ) {
        composeRule.captureScreenshotDeterministic(
            name = "SettingsHierarchy_" + page + "_" + variant + "_" + (if (dark) "dark" else "light"),
            darkTheme = dark,
        ) {
            AppTheme(themeMode = mode, seedColor = seed) {
                when (page) {
                    "hub" -> {
                        SettingsScreen(
                            viewModel = viewModel(dark = dark),
                            onBackClick = {},
                            onOpenPreferences = {},
                            onOpenAccount = {},
                        )
                    }

                    "preferences" -> {
                        SettingsPreferencesScreen({}, {}, {}, {}, {}, {})
                    }

                    "appearance" -> {
                        SettingsAppearanceScreen(viewModel = viewModel(dark = dark), onBackClick = {})
                    }

                    else -> {
                        SettingsInterfaceScreen(viewModel = viewModel(dark = dark), onBackClick = {})
                    }
                }
            }
        }
    }
}
