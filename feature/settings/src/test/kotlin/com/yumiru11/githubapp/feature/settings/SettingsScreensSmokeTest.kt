package com.yumiru11.githubapp.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import com.yumiru11.githubapp.core.githubrest.http.RateLimitStore
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
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
 * 设置模块各页的**语义冒烟**（非截图）：渲染每页并断言其标题文案真的上屏。
 *
 * 与截图基线的分工：像素比对已随 Roborazzi 体系整体移除（2026-10），本文件接管
 * 「每个设置页都能组合、偏好快照能驱动出可见标题」的回归职责，同时保留覆盖率门禁
 * 所需的执行路径。页面本身不自带主题，故显式包一层 [AppTheme]。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsScreensSmokeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(): SettingsViewModel {
        val preferences = mockk<UserPreferencesRepository>(relaxed = true)
        every { preferences.themeMode } returns MutableStateFlow(ThemeMode.LIGHT)
        val tokenStorage = mockk<TokenStorage>(relaxed = true)
        val sessionManager = mockk<OAuthSessionManager>(relaxed = true)
        every { sessionManager.authState } returns MutableStateFlow(AuthState.Anonymous)
        val rateLimitStore = mockk<RateLimitStore>(relaxed = true)
        every { rateLimitStore.snapshot } returns MutableStateFlow(null)
        return SettingsViewModel(preferences, tokenStorage, sessionManager, rateLimitStore)
    }

    private fun render(
        titleRes: Int,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) { content() }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(titleRes)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_rendersTitle() {
        render(R.string.settings_title) {
            SettingsScreen(
                viewModel = viewModel(),
                onBackClick = {},
                onOpenPreferences = {},
                onOpenAccount = {},
            )
        }
    }

    @Test
    fun settingsPreferencesScreen_rendersTitle() {
        render(R.string.settings_preferences) {
            SettingsPreferencesScreen({}, {}, {}, {}, {})
        }
    }

    @Test
    fun settingsAppearanceScreen_rendersTitle() {
        render(R.string.settings_appearance_group) {
            SettingsAppearanceScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun settingsInterfaceScreen_rendersTitle() {
        render(R.string.settings_interface) {
            SettingsInterfaceScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun settingsEditorScreen_rendersTitle() {
        render(R.string.settings_editor) {
            SettingsEditorScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun settingsGeneralScreen_rendersTitle() {
        render(R.string.settings_general_entry) {
            SettingsGeneralScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun settingsAccountScreen_rendersTitle() {
        render(R.string.settings_account_entry) {
            SettingsAccountScreen(viewModel = viewModel(), onBackClick = {})
        }
    }
}
