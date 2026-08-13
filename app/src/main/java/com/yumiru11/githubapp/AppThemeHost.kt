package com.yumiru11.githubapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.model.resolveEffectiveThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme

/**
 * 主题宿主：把 [UserPreferencesRepository] 持久化的偏好接到
 * core:designsystem 的 [AppTheme]（T6 Wave2 装配 + T24 扩展）。
 *
 * - [UserPreferencesRepository.themeMode] 默认 [ThemeMode.SYSTEM]（跟随系统，
 *   ADR-0004 / spec 决策）；初始值取 SYSTEM 与仓库默认一致，避免首帧闪烁
 * - T24：动态取色/OLED/高对比开关经 [resolveEffectiveThemeMode] 合成生效模式；
 *   seed 色（[UserPreferencesRepository.seedColor]）传给 [AppTheme] 作用于基础模式
 * - 仓库 Flow 发射新值（T24 设置页 setXxx）→ collectAsStateWithLifecycle
 *   重组 → [AppTheme] 切换色板，无需 activity 重启
 * - 单一测试缝：装配行为在 AppThemeHostTest 用假仓库验证（色板选择/重组）
 *
 * @param repository 用户偏好仓库（Hilt 注入的单例）
 * @param content 主题之下的界面内容
 */
@Composable
fun AppThemeHost(
    repository: UserPreferencesRepository,
    content: @Composable () -> Unit,
) {
    val themeMode by repository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColorEnabled by repository.dynamicColorEnabled.collectAsStateWithLifecycle(initialValue = false)
    val oledEnabled by repository.oledEnabled.collectAsStateWithLifecycle(initialValue = false)
    val highContrastEnabled by repository.highContrastEnabled.collectAsStateWithLifecycle(initialValue = false)
    val seedColor by repository.seedColor.collectAsStateWithLifecycle(initialValue = UserPreferencesRepository.DEFAULT_SEED_COLOR)
    val effectiveMode =
        resolveEffectiveThemeMode(
            base = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            oledEnabled = oledEnabled,
            highContrastEnabled = highContrastEnabled,
        )
    AppTheme(
        themeMode = effectiveMode,
        seedColor = Color(seedColor),
        content = content,
    )
}
