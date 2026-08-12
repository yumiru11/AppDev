package com.yumiru11.githubapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.theme.darkPalette
import com.yumiru11.githubapp.core.designsystem.theme.highContrastLightPalette
import com.yumiru11.githubapp.core.designsystem.theme.lightPalette
import com.yumiru11.githubapp.core.designsystem.theme.oledPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AppThemeHost 主题装配测试（T6 Wave2）：UserPreferencesRepository 持久化的
 * ThemeMode → AppTheme 色板选择链路。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 * 动态取色（DYNAMIC_*）不在本测试覆盖：Robolectric 无壁纸来源（T6
 * ThemePaletteTest 同先例），需真机 API 31+ 验证，见停手条件标注。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppThemeHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeHost_systemMode_followsSystemPalette() {
        var capturedBackground: Color? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository()) {
                    capturedBackground = MaterialTheme.colorScheme.background
                }
            }
        }
        composeRule.waitForIdle()

        // Robolectric 默认日间模式 → SYSTEM 解析为亮色色板
        assertEquals(lightPalette().colorScheme.background, capturedBackground)
    }

    @Test
    fun themeHost_darkMode_selectsDarkPalette() {
        var capturedBackground: Color? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository(themeMode = ThemeMode.DARK)) {
                    capturedBackground = MaterialTheme.colorScheme.background
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(darkPalette().colorScheme.background, capturedBackground)
    }

    @Test
    fun themeHost_oledMode_selectsOledPalette() {
        var capturedBackground: Color? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository(themeMode = ThemeMode.OLED)) {
                    capturedBackground = MaterialTheme.colorScheme.background
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(oledPalette().colorScheme.background, capturedBackground)
    }

    @Test
    fun themeHost_highContrastMode_selectsHighContrastPalette() {
        var capturedBackground: Color? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository(themeMode = ThemeMode.HIGH_CONTRAST)) {
                    capturedBackground = MaterialTheme.colorScheme.background
                }
            }
        }
        composeRule.waitForIdle()

        // HIGH_CONTRAST 跟随系统亮/暗；Robolectric 默认日间 → 高对比亮色
        assertEquals(highContrastLightPalette().colorScheme.background, capturedBackground)
    }

    @Test
    fun themeHost_emittedModeChange_recomposesToNewPalette() {
        val repository = FakeUserPreferencesRepository(themeMode = ThemeMode.SYSTEM)
        var capturedBackground: Color? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = repository) {
                    capturedBackground = MaterialTheme.colorScheme.background
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(lightPalette().colorScheme.background, capturedBackground)

        // 模拟设置页持久化写入（T24）：repo Flow 发新值 → AppTheme 重组换色板
        repository.themeModeFlow.value = ThemeMode.OLED
        composeRule.waitForIdle()

        assertEquals(oledPalette().colorScheme.background, capturedBackground)
    }
}

/**
 * 测试用假仓库：ThemeMode 可编程注入 / 运行时发射（零 DataStore）。
 */
private class FakeUserPreferencesRepository(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) : UserPreferencesRepository {
    val themeModeFlow = MutableStateFlow(themeMode)

    private val blurEnabledFlow = MutableStateFlow(true)
    private val languageTagFlow = MutableStateFlow<String?>(null)

    override val themeMode: Flow<ThemeMode> = themeModeFlow

    override val languageTag: Flow<String?> = languageTagFlow

    override val blurEnabled: Flow<Boolean> = blurEnabledFlow

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }

    override suspend fun setBlurEnabled(enabled: Boolean) {
        blurEnabledFlow.value = enabled
    }

    override suspend fun setLanguageTag(tag: String?) {
        languageTagFlow.value = tag
    }
}

/**
 * 常驻 RESUMED 的测试 LifecycleOwner（collectAsStateWithLifecycle 依赖）。
 */
private class TestLifecycleOwner : LifecycleOwner {
    private val registry: LifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

    override val lifecycle: Lifecycle = registry
}
