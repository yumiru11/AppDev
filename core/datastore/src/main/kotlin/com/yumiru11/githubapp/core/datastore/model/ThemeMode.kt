package com.yumiru11.githubapp.core.datastore.model

/**
 * 应用主题模式（用户偏好，UI 层据此决定亮/暗/跟随系统）。
 *
 * 纯 Kotlin 枚举；持久化为 name 字符串，未知值回退 [SYSTEM]。
 */
enum class ThemeMode {
    /** 跟随系统 */
    SYSTEM,

    /** 强制亮色 */
    LIGHT,

    /** 强制暗色 */
    DARK,
}
