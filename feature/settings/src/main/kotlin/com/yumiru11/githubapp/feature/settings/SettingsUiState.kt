package com.yumiru11.githubapp.feature.settings

import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.AuthState

/**
 * 设置页 UI 状态（全部偏好字段的一次快照，T24）。
 *
 * 由 [SettingsViewModel] 从 [UserPreferencesRepository] 各 Flow combine 而成；
 * 默认值与仓库默认值一致（首帧不闪烁）。
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val seedColor: Long = UserPreferencesRepository.DEFAULT_SEED_COLOR,
    val oledEnabled: Boolean = false,
    val highContrastEnabled: Boolean = false,
    val cornerScale: Float = UserPreferencesRepository.DEFAULT_CORNER_SCALE,
    val motionScale: Float = UserPreferencesRepository.DEFAULT_MOTION_SCALE,
    val iconStyle: IconStyle = IconStyle.ROUNDED,
    val codeFont: CodeFont = CodeFont.MONO,
    val codeLineNumbers: Boolean = true,
    val languageTag: String? = null,
    val blurEnabled: Boolean = true,
    val authState: AuthState = AuthState.Anonymous,
)
