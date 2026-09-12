package com.yumiru11.githubapp.feature.settings

import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubrest.http.RateLimitSnapshot

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
    /** 毛玻璃总开关（#167 / UI03） */
    val blurEnabled: Boolean = true,
    /** 列表首屏 stagger 开关（#167 / UI06，§4.2 H2-2） */
    val staggerEnabled: Boolean = true,
    /** 毛玻璃逐项开关：顶栏 / 底栏 / 通知面板 / BottomSheet（ui-design §6.3 四条允许点位） */
    val glassTopBar: Boolean = true,
    val glassBottomBar: Boolean = true,
    val glassPanel: Boolean = true,
    val glassBottomSheet: Boolean = true,
    /** 全局背景图 URI（#167 / UI04；null = 默认无图） */
    val backgroundImageUri: String? = null,
    /** 背景图统一不透明度（#167 / UI04，§7.4） */
    val backgroundOpacity: Float = 0.25f,
    val authState: AuthState = AuthState.Anonymous,
    /**
     * 最近一次观测到的 GitHub 限流快照（GATE-2）。
     *
     * 来源 = core:github-rest 的 RateLimitStore（EtagCacheInterceptor 在每条响应上录制）；
     * null = 本次进程尚无任何带限流头的响应（开发者分组显示「暂无数据」占位，非错误态）。
     */
    val rateLimit: RateLimitSnapshot? = null,
) {
    /**
     * 毛玻璃逐项开关是否可交互：OLED / 高对比下 §6.3 强制禁用（此时总开关也被覆盖），
     * 逐项开关置灰更诚实 —— 避免"打开了却看不到效果"的困惑。
     */
    val glassPerItemEnabled: Boolean
        get() = blurEnabled && !oledEnabled && !highContrastEnabled
}
