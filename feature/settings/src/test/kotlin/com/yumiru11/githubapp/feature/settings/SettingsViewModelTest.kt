package com.yumiru11.githubapp.feature.settings

import app.cash.turbine.test
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenEndpointClient
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeResult
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * SettingsViewModel 测试（T24）。
 *
 * - 各 setter → 偏好仓库持久化 → uiState 即时发射（即时生效链路）
 * - 默认值与仓库默认一致（首帧不闪烁）
 * - PAT 保存 → TokenStorage 落盘（isRestOnly=true）→ authState 变 PAT（ADR-0003）
 * - 空白 PAT → 忽略
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_byDefault_emitsDefaults() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(ThemeMode.SYSTEM, state.themeMode)
                assertEquals(false, state.dynamicColorEnabled)
                assertEquals(UserPreferencesRepository.DEFAULT_SEED_COLOR, state.seedColor)
                assertEquals(false, state.oledEnabled)
                assertEquals(false, state.highContrastEnabled)
                assertEquals(UserPreferencesRepository.DEFAULT_CORNER_SCALE, state.cornerScale)
                assertEquals(UserPreferencesRepository.DEFAULT_MOTION_SCALE, state.motionScale)
                assertEquals(IconStyle.ROUNDED, state.iconStyle)
                assertEquals(CodeFont.MONO, state.codeFont)
                assertEquals(true, state.codeLineNumbers)
                assertNull(state.languageTag)
                assertEquals(true, state.blurEnabled)
            }
        }

    @Test
    fun setThemeMode_dark_persistsAndEmitsThemeMode() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setThemeMode(ThemeMode.DARK)
                assertEquals(ThemeMode.DARK, awaitItem().themeMode)
            }
        }

    @Test
    fun setDynamicColorEnabled_true_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setDynamicColorEnabled(true)
                assertEquals(true, awaitItem().dynamicColorEnabled)
            }
        }

    @Test
    fun setSeedColor_customColor_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setSeedColor(0xFF7C3AED)
                assertEquals(0xFF7C3AED, awaitItem().seedColor)
            }
        }

    @Test
    fun setOledEnabled_true_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setOledEnabled(true)
                assertEquals(true, awaitItem().oledEnabled)
            }
        }

    @Test
    fun setHighContrastEnabled_true_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setHighContrastEnabled(true)
                assertEquals(true, awaitItem().highContrastEnabled)
            }
        }

    @Test
    fun setCornerScale_1_5_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setCornerScale(1.5f)
                assertEquals(1.5f, awaitItem().cornerScale)
            }
        }

    @Test
    fun setMotionScale_0_5_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setMotionScale(0.5f)
                assertEquals(0.5f, awaitItem().motionScale)
            }
        }

    @Test
    fun setIconStyle_filled_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setIconStyle(IconStyle.FILLED)
                assertEquals(IconStyle.FILLED, awaitItem().iconStyle)
            }
        }

    @Test
    fun setCodeFont_system_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setCodeFont(CodeFont.SYSTEM)
                assertEquals(CodeFont.SYSTEM, awaitItem().codeFont)
            }
        }

    @Test
    fun setCodeLineNumbers_false_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setCodeLineNumbers(false)
                assertEquals(false, awaitItem().codeLineNumbers)
            }
        }

    @Test
    fun setLanguageTag_zh_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setLanguageTag("zh-rCN")
                assertEquals("zh-rCN", awaitItem().languageTag)
            }
        }

    @Test
    fun setBlurEnabled_false_persistsAndEmits() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.setBlurEnabled(false)
                assertEquals(false, awaitItem().blurEnabled)
            }
        }

    @Test
    fun savePat_valid_savesRestOnlySessionAndRefreshesToPat() =
        runTest {
            val storage = InMemoryTokenStorage()
            val manager = OAuthSessionManager(storage, FakeTokenEndpointClient(), OAuthConfig())
            val viewModel = SettingsViewModel(FakeUserPreferencesRepository(), storage, manager)

            viewModel.savePat("ghp_test_pat")

            assertEquals(AuthState.PAT, manager.authState.value)
            val session = storage.loadSession()
            assertEquals("ghp_test_pat", session.pat)
            assertTrue("PAT 会话必须 isRestOnly（ADR-0003）", session.isRestOnly)
        }

    @Test
    fun savePat_blankPat_isIgnored() =
        runTest {
            val storage = InMemoryTokenStorage()
            val manager = OAuthSessionManager(storage, FakeTokenEndpointClient(), OAuthConfig())
            val viewModel = SettingsViewModel(FakeUserPreferencesRepository(), storage, manager)

            viewModel.savePat("   ")

            assertEquals(AuthState.Anonymous, manager.authState.value)
            assertNull(storage.loadSession().pat)
        }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            preferences = FakeUserPreferencesRepository(),
            tokenStorage = InMemoryTokenStorage(),
            sessionManager = OAuthSessionManager(InMemoryTokenStorage(), FakeTokenEndpointClient(), OAuthConfig()),
        )
}

/** 假 [TokenEndpointClient]：记录收到的 code，返回固定 token（零网络）。 */
private class FakeTokenEndpointClient : TokenEndpointClient {
    var lastCode: String? = null

    override suspend fun exchangeCode(code: String): TokenExchangeResult {
        lastCode = code
        return TokenExchangeResult(accessToken = "gho_test_access", refreshToken = "ghr_test_refresh")
    }
}

/** 假偏好仓库：全部字段 MutableStateFlow 可编程注入/发射（零 DataStore）。 */
private class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val languageTagFlow = MutableStateFlow<String?>(null)
    private val blurEnabledFlow = MutableStateFlow(true)
    private val dynamicColorEnabledFlow = MutableStateFlow(false)
    private val seedColorFlow = MutableStateFlow(UserPreferencesRepository.DEFAULT_SEED_COLOR)
    private val oledEnabledFlow = MutableStateFlow(false)
    private val highContrastEnabledFlow = MutableStateFlow(false)
    private val cornerScaleFlow = MutableStateFlow(UserPreferencesRepository.DEFAULT_CORNER_SCALE)
    private val motionScaleFlow = MutableStateFlow(UserPreferencesRepository.DEFAULT_MOTION_SCALE)
    private val iconStyleFlow = MutableStateFlow(IconStyle.ROUNDED)
    private val codeFontFlow = MutableStateFlow(CodeFont.MONO)
    private val codeLineNumbersFlow = MutableStateFlow(true)

    override val themeMode: Flow<ThemeMode> = themeModeFlow

    override val languageTag: Flow<String?> = languageTagFlow

    override val blurEnabled: Flow<Boolean> = blurEnabledFlow

    override val dynamicColorEnabled: Flow<Boolean> = dynamicColorEnabledFlow

    override val seedColor: Flow<Long> = seedColorFlow

    override val oledEnabled: Flow<Boolean> = oledEnabledFlow

    override val highContrastEnabled: Flow<Boolean> = highContrastEnabledFlow

    override val cornerScale: Flow<Float> = cornerScaleFlow

    override val motionScale: Flow<Float> = motionScaleFlow

    override val iconStyle: Flow<IconStyle> = iconStyleFlow

    override val codeFont: Flow<CodeFont> = codeFontFlow

    override val codeLineNumbers: Flow<Boolean> = codeLineNumbersFlow

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }

    override suspend fun setBlurEnabled(enabled: Boolean) {
        blurEnabledFlow.value = enabled
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
}
