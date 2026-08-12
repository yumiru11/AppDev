package com.yumiru11.githubapp.core.datastore.model

/**
 * 应用主题模式（用户偏好，UI 层据此决定亮/暗/跟随系统/动态取色等）。
 *
 * 纯 Kotlin 枚举；持久化为 name 字符串，未知值回退 [SYSTEM]。
 * 扩展枚举值不影响旧持久化数据——旧数据仅含 SYSTEM/LIGHT/DARK，name 依然有效。
 */
enum class ThemeMode {
    /** 跟随系统 */
    SYSTEM,

    /** 强制亮色 */
    LIGHT,

    /** 强制暗色 */
    DARK,

    /** OLED 纯黑省电主题（不依赖系统 API）。 */
    OLED,

    /** Android 12+（API 31+）壁纸动态取色的亮色变体；低版本回退亮色。 */
    DYNAMIC_LIGHT,

    /** Android 12+（API 31+）壁纸动态取色的暗色变体；低版本回退暗色。 */
    DYNAMIC_DARK,

    /** 无障碍高对比主题（跟随系统亮/暗）。 */
    HIGH_CONTRAST,
}
