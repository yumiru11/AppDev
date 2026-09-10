package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 毛玻璃逐项开关的运行时快照（ui-design §6.3，issue #167 / UI03）。
 *
 * 与 [GlassRenderPolicy] 的分工：
 * - [GlassSettings] 回答「用户**允许**哪些点位用玻璃」（偏好层，4 个逐项开关 + 总开关）
 * - [GlassRenderPolicy] 回答「这个点位此刻**能不能**真模糊」（运行期：HazeState 有没有、
 *   API 是否 ≥31）
 *
 * 两者都不满足时任一路径都降级为纯半透明 scrim，视觉一致（§6.2）。
 *
 * 无障碍/省电强制项（§6.3 用户拍板）：OLED 纯黑与高对比主题下**一律禁用玻璃与背景图**
 * —— 由 [withAccessibilityOverrides] 统一裁决，避免各消费点各写一遍。
 */
data class GlassSettings(
    /** 总开关（设置页「毛玻璃效果」；false 时四个点位全部降级） */
    val masterEnabled: Boolean = true,
    val topBarEnabled: Boolean = true,
    val bottomBarEnabled: Boolean = true,
    val panelEnabled: Boolean = true,
    val bottomSheetEnabled: Boolean = true,
) {
    /** 该点位此刻是否允许玻璃（总开关 ∧ 逐项开关） */
    fun enabledFor(scope: GlassScope): Boolean =
        masterEnabled &&
            when (scope) {
                GlassScope.TOP_BAR -> topBarEnabled
                GlassScope.BOTTOM_BAR -> bottomBarEnabled
                GlassScope.PANEL -> panelEnabled
                GlassScope.BOTTOM_SHEET -> bottomSheetEnabled
            }

    /**
     * 叠加 OLED / 高对比的强制禁用（ui-design §6.3 用户拍板 F2-1 / F1-3）。
     * 纯函数，单测断言"OLED 或高对比任一开启 → 全部点位关闭"。
     */
    fun withAccessibilityOverrides(
        oledEnabled: Boolean,
        highContrastEnabled: Boolean,
    ): GlassSettings = if (oledEnabled || highContrastEnabled) copy(masterEnabled = false) else this
}

/**
 * 全局玻璃设置（由 app 层 AppThemeHost 从 UserPreferencesRepository 注入）。
 *
 * static：值为偏好快照，变化时整体重组（GlassSurface 的 effect 挂载点必须整体重算，
 * 逐帧读取没有意义）。默认全开 —— 截图测试/预览不提供时行为与 #83 之前一致。
 */
val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }
