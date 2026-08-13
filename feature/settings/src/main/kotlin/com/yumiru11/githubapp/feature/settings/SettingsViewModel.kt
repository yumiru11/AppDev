package com.yumiru11.githubapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel（T24）。
 *
 * - [uiState]：全部偏好 + 登录态 combine 快照（单一事实来源，UI 只读）
 * - 各 setter 写 [UserPreferencesRepository]（DataStore 持久化）→ Flow 发射 →
 *   AppThemeHost/设置页即时重组（无需重启）
 * - [savePat]：开发者 PAT 模式，落盘 TokenStorage（isRestOnly=true，ADR-0003）
 *   后刷新登录态为 PAT（与 feature:auth AuthViewModel 同路径）
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferences: UserPreferencesRepository,
        private val tokenStorage: TokenStorage,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                combine(
                    preferences.themeMode,
                    preferences.dynamicColorEnabled,
                    preferences.seedColor,
                    preferences.oledEnabled,
                    preferences.highContrastEnabled,
                ) { themeMode, dynamicColor, seedColor, oled, highContrast ->
                    ThemePrefs(themeMode, dynamicColor, seedColor, oled, highContrast)
                },
                combine(
                    preferences.cornerScale,
                    preferences.motionScale,
                    preferences.iconStyle,
                    preferences.codeFont,
                    preferences.codeLineNumbers,
                ) { cornerScale, motionScale, iconStyle, codeFont, lineNumbers ->
                    StylePrefs(cornerScale, motionScale, iconStyle, codeFont, lineNumbers)
                },
                preferences.languageTag,
                preferences.blurEnabled,
                sessionManager.authState,
            ) { theme, style, languageTag, blurEnabled, authState ->
                SettingsUiState(
                    themeMode = theme.themeMode,
                    dynamicColorEnabled = theme.dynamicColorEnabled,
                    seedColor = theme.seedColor,
                    oledEnabled = theme.oledEnabled,
                    highContrastEnabled = theme.highContrastEnabled,
                    cornerScale = style.cornerScale,
                    motionScale = style.motionScale,
                    iconStyle = style.iconStyle,
                    codeFont = style.codeFont,
                    codeLineNumbers = style.codeLineNumbers,
                    languageTag = languageTag,
                    blurEnabled = blurEnabled,
                    authState = authState,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { preferences.setThemeMode(mode) }
        }

        fun setDynamicColorEnabled(enabled: Boolean) {
            viewModelScope.launch { preferences.setDynamicColorEnabled(enabled) }
        }

        fun setSeedColor(color: Long) {
            viewModelScope.launch { preferences.setSeedColor(color) }
        }

        fun setOledEnabled(enabled: Boolean) {
            viewModelScope.launch { preferences.setOledEnabled(enabled) }
        }

        fun setHighContrastEnabled(enabled: Boolean) {
            viewModelScope.launch { preferences.setHighContrastEnabled(enabled) }
        }

        fun setCornerScale(scale: Float) {
            viewModelScope.launch { preferences.setCornerScale(scale) }
        }

        fun setMotionScale(scale: Float) {
            viewModelScope.launch { preferences.setMotionScale(scale) }
        }

        fun setIconStyle(style: IconStyle) {
            viewModelScope.launch { preferences.setIconStyle(style) }
        }

        fun setCodeFont(font: CodeFont) {
            viewModelScope.launch { preferences.setCodeFont(font) }
        }

        fun setCodeLineNumbers(enabled: Boolean) {
            viewModelScope.launch { preferences.setCodeLineNumbers(enabled) }
        }

        fun setBlurEnabled(enabled: Boolean) {
            viewModelScope.launch { preferences.setBlurEnabled(enabled) }
        }

        /** null 表示回退系统语言。 */
        fun setLanguageTag(tag: String?) {
            viewModelScope.launch { preferences.setLanguageTag(tag) }
        }

        /** 开发者模式：保存 PAT（REST-only，ADR-0003）并刷新登录态为 PAT；空白输入忽略。 */
        fun savePat(pat: String) {
            if (pat.isBlank()) return
            tokenStorage.saveSession(SessionData(pat = pat, isRestOnly = true))
            viewModelScope.launch { sessionManager.refreshState() }
        }
    }

/** 主题相关偏好中间聚合（combine 5 流上限内分组）。 */
private data class ThemePrefs(
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val seedColor: Long,
    val oledEnabled: Boolean,
    val highContrastEnabled: Boolean,
)

/** 样式相关偏好中间聚合（combine 5 流上限内分组）。 */
private data class StylePrefs(
    val cornerScale: Float,
    val motionScale: Float,
    val iconStyle: IconStyle,
    val codeFont: CodeFont,
    val codeLineNumbers: Boolean,
)
