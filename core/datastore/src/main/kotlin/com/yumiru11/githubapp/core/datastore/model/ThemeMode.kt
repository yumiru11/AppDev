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

/**
 * 把设置页的分立开关合成「生效主题模式」（T24 设置页 + AppThemeHost 消费）。
 *
 * [base] 来自主题模式选择（System/Light/Dark）；开关优先级（无障碍优先）：
 * 高对比 > OLED 纯黑 > 动态取色 > 基础模式。动态取色按 [base] 亮暗选
 * DYNAMIC_LIGHT/DYNAMIC_DARK（Android 12+ 壁纸取色，低版本由色板函数回退）。
 */
fun resolveEffectiveThemeMode(
    base: ThemeMode,
    dynamicColorEnabled: Boolean,
    oledEnabled: Boolean,
    highContrastEnabled: Boolean,
    systemDark: Boolean = false,
): ThemeMode =
    when {
        highContrastEnabled -> {
            ThemeMode.HIGH_CONTRAST
        }

        oledEnabled -> {
            ThemeMode.OLED
        }

        dynamicColorEnabled -> {
            when (base) {
                ThemeMode.DARK -> ThemeMode.DYNAMIC_DARK

                // SYSTEM 跟随系统明暗（P0-3：原实现写死 DYNAMIC_LIGHT）
                ThemeMode.SYSTEM -> if (systemDark) ThemeMode.DYNAMIC_DARK else ThemeMode.DYNAMIC_LIGHT

                else -> ThemeMode.DYNAMIC_LIGHT
            }
        }

        else -> {
            base
        }
    }
