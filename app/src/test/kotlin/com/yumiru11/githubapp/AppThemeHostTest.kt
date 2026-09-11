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
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.RepoLayoutMode
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.theme.darkPalette
import com.yumiru11.githubapp.core.designsystem.theme.highContrastDarkPalette
import com.yumiru11.githubapp.core.designsystem.theme.highContrastLightPalette
import com.yumiru11.githubapp.core.designsystem.theme.lightPalette
import com.yumiru11.githubapp.core.designsystem.theme.oledPalette
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.GlassSettings
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import com.yumiru11.githubapp.core.designsystem.token.LocalIconStyle
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
    fun themeHost_defaultPrefs_providesFullyEnabledGlassSettings() {
        var captured: GlassSettings? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository()) {
                    captured = LocalGlassSettings.current
                }
            }
        }
        composeRule.waitForIdle()

        val settings = captured!!
        GlassScope.entries.forEach { scope ->
            assertEquals("默认应全开：$scope", true, settings.enabledFor(scope))
        }
    }

    @Test
    fun themeHost_iconStylePreference_providesLocalIconStyle() {
        // #168 / UI12：设置页选的图标风格经 LocalIconStyle 下发（AppIcon 据此取变体）
        var captured: IconStyle? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository(iconStyle = IconStyle.FILLED)) {
                    captured = LocalIconStyle.current
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(IconStyle.FILLED, captured)
    }

    @Test
    fun themeHost_iconStyleChange_recomposesToNewStyle() {
        // 「切换后立即变化」：仓库 Flow 发射新值 → LocalIconStyle 跟随重组（无需重启）
        val repository = FakeUserPreferencesRepository(iconStyle = IconStyle.ROUNDED)
        val seen = mutableListOf<IconStyle>()

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = repository) {
                    seen += LocalIconStyle.current
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { repository.iconStyleFlow.value = IconStyle.OUTLINED }
        composeRule.waitForIdle()

        assertEquals(IconStyle.OUTLINED, seen.last())
    }

    @Test
    fun themeHost_panelSwitchOff_disablesOnlyPanelScope() {
        var captured: GlassSettings? = null
        val repository = FakeUserPreferencesRepository(glassPanel = false)

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = repository) {
                    captured = LocalGlassSettings.current
                }
            }
        }
        composeRule.waitForIdle()

        val settings = captured!!
        assertEquals(false, settings.enabledFor(GlassScope.PANEL))
        assertEquals(true, settings.enabledFor(GlassScope.TOP_BAR))
        assertEquals(true, settings.enabledFor(GlassScope.BOTTOM_BAR))
        assertEquals(true, settings.enabledFor(GlassScope.BOTTOM_SHEET))
    }

    @Test
    fun themeHost_oledEnabled_forcesAllScopesOff() {
        var captured: GlassSettings? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                // OLED 生效 → §6.3 强制禁用玻璃（即使总开关与逐项开关都开着）
                AppThemeHost(repository = FakeUserPreferencesRepository(oledEnabled = true)) {
                    captured = LocalGlassSettings.current
                }
            }
        }
        composeRule.waitForIdle()

        val settings = captured!!
        assertEquals(false, settings.masterEnabled)
        GlassScope.entries.forEach { scope ->
            assertEquals("OLED 下应全关：$scope", false, settings.enabledFor(scope))
        }
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

    // ── 系统深色跟随（E7）：isSystemInDarkTheme → resolveEffectiveThemeMode(systemDark) 接线 ──

    @Test
    @Config(qualifiers = "night")
    fun themeHost_systemMode_systemDark_selectsDarkPalette() {
        // 跟随系统模式 + 系统深色（Robolectric night qualifier）→ 深色色板
        var capturedBackground: Color? = null

        composeRule.setContent {
            val lifecycleOwner = remember { TestLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AppThemeHost(repository = FakeUserPreferencesRepository(themeMode = ThemeMode.SYSTEM)) {
                    capturedBackground = MaterialTheme.colorScheme.background
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(darkPalette().colorScheme.background, capturedBackground)
    }

    @Test
    @Config(qualifiers = "night")
    fun themeHost_highContrastMode_systemDark_selectsHighContrastDarkPalette() {
        // HIGH_CONTRAST 跟随系统明暗：系统深色 → 高对比深色色板
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

        assertEquals(highContrastDarkPalette().colorScheme.background, capturedBackground)
    }

    @Test
    @Config(qualifiers = "night")
    fun themeHost_darkMode_systemDark_staysDarkPalette() {
        // 强制 DARK 模式不受系统深色影响（跟随系统仅作用于 SYSTEM/HIGH_CONTRAST）
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
}

/**
 * 测试用假仓库：ThemeMode 可编程注入 / 运行时发射（零 DataStore）。
 */
private class FakeUserPreferencesRepository(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    oledEnabled: Boolean = false,
    glassPanel: Boolean = true,
    iconStyle: IconStyle = IconStyle.ROUNDED,
) : UserPreferencesRepository {
    val themeModeFlow = MutableStateFlow(themeMode)

    private val blurEnabledFlow = MutableStateFlow(true)
    private val glassTopBarFlow = MutableStateFlow(true)
    private val glassBottomBarFlow = MutableStateFlow(true)
    private val glassPanelFlow = MutableStateFlow(glassPanel)
    private val glassBottomSheetFlow = MutableStateFlow(true)
    private val languageTagFlow = MutableStateFlow<String?>(null)
    private val dynamicColorEnabledFlow = MutableStateFlow(false)
    private val seedColorFlow = MutableStateFlow(UserPreferencesRepository.DEFAULT_SEED_COLOR)
    private val oledEnabledFlow = MutableStateFlow(oledEnabled)
    private val highContrastEnabledFlow = MutableStateFlow(false)
    private val cornerScaleFlow = MutableStateFlow(UserPreferencesRepository.DEFAULT_CORNER_SCALE)
    private val motionScaleFlow = MutableStateFlow(UserPreferencesRepository.DEFAULT_MOTION_SCALE)
    val iconStyleFlow = MutableStateFlow(iconStyle)
    private val codeFontFlow = MutableStateFlow(CodeFont.MONO)
    private val codeLineNumbersFlow = MutableStateFlow(true)

    override val themeMode: Flow<ThemeMode> = themeModeFlow

    override val languageTag: Flow<String?> = languageTagFlow

    override val blurEnabled: Flow<Boolean> = blurEnabledFlow

    override val glassTopBar: Flow<Boolean> = glassTopBarFlow

    override val glassBottomBar: Flow<Boolean> = glassBottomBarFlow

    override val glassPanel: Flow<Boolean> = glassPanelFlow

    override val glassBottomSheet: Flow<Boolean> = glassBottomSheetFlow

    override val dynamicColorEnabled: Flow<Boolean> = dynamicColorEnabledFlow

    override val seedColor: Flow<Long> = seedColorFlow

    override val oledEnabled: Flow<Boolean> = oledEnabledFlow

    override val highContrastEnabled: Flow<Boolean> = highContrastEnabledFlow

    override val cornerScale: Flow<Float> = cornerScaleFlow

    override val motionScale: Flow<Float> = motionScaleFlow

    override val iconStyle: Flow<IconStyle> = iconStyleFlow

    override val codeFont: Flow<CodeFont> = codeFontFlow

    override val codeLineNumbers: Flow<Boolean> = codeLineNumbersFlow

    override val repoLayout: Flow<RepoLayoutMode> = MutableStateFlow(RepoLayoutMode.LIST)

    private val staggerEnabledFlow = MutableStateFlow(true)

    override val staggerEnabled: Flow<Boolean> = staggerEnabledFlow

    private val backgroundUriFlow = MutableStateFlow<String?>(null)
    private val backgroundOpacityFlow = MutableStateFlow(0.25f)

    override val backgroundImageUri: Flow<String?> = backgroundUriFlow

    override val backgroundOpacity: Flow<Float> = backgroundOpacityFlow

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }

    override suspend fun setBlurEnabled(enabled: Boolean) {
        blurEnabledFlow.value = enabled
    }

    override suspend fun setGlassTopBar(enabled: Boolean) {
        glassTopBarFlow.value = enabled
    }

    override suspend fun setGlassBottomBar(enabled: Boolean) {
        glassBottomBarFlow.value = enabled
    }

    override suspend fun setGlassPanel(enabled: Boolean) {
        glassPanelFlow.value = enabled
    }

    override suspend fun setGlassBottomSheet(enabled: Boolean) {
        glassBottomSheetFlow.value = enabled
    }

    override suspend fun setLanguageTag(tag: String?) {
        languageTagFlow.value = tag
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dynamicColorEnabledFlow.value = enabled
    }

    override suspend fun setSeedColor(color: Long) {
        seedColorFlow.value = color
    }

    override suspend fun setOledEnabled(enabled: Boolean) {
        oledEnabledFlow.value = enabled
    }

    override suspend fun setHighContrastEnabled(enabled: Boolean) {
        highContrastEnabledFlow.value = enabled
    }

    override suspend fun setCornerScale(scale: Float) {
        cornerScaleFlow.value = scale
    }

    override suspend fun setMotionScale(scale: Float) {
        motionScaleFlow.value = scale
    }

    override suspend fun setIconStyle(style: IconStyle) {
        iconStyleFlow.value = style
    }

    override suspend fun setCodeFont(font: CodeFont) {
        codeFontFlow.value = font
    }

    override suspend fun setCodeLineNumbers(enabled: Boolean) {
        codeLineNumbersFlow.value = enabled
    }

    override suspend fun setStaggerEnabled(enabled: Boolean) {
        staggerEnabledFlow.value = enabled
    }

    override suspend fun setBackgroundImageUri(uri: String?) {
        backgroundUriFlow.value = uri
    }

    override suspend fun setBackgroundOpacity(opacity: Float) {
        backgroundOpacityFlow.value = opacity
    }

    override suspend fun setRepoLayout(mode: RepoLayoutMode) {
        // 设置页不暴露布局开关（仓库分区右上角按钮直接写偏好），此处仅满足接口契约
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
