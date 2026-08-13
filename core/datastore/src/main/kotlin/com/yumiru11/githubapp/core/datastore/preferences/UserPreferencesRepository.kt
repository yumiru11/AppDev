package com.yumiru11.githubapp.core.datastore.preferences

import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * 用户偏好仓库接口（UI 层只依赖该抽象，Hilt @Binds 装配实现）。
 */
interface UserPreferencesRepository {
    companion object {
        /** 默认主题色（GitHub brand blue，与 designsystem lightPalette primary 一致） */
        const val DEFAULT_SEED_COLOR: Long = 0xFF0969DA

        /** 默认圆角强度缩放（1.0 = AppDimens 原值） */
        const val DEFAULT_CORNER_SCALE: Float = 1f

        /** 默认动画强度缩放（1.0 = AppMotion 原值） */
        const val DEFAULT_MOTION_SCALE: Float = 1f
    }

    /** 主题模式（默认跟随系统） */
    val themeMode: Flow<ThemeMode>

    /** 语言标签（BCP 47，null = 跟随系统语言） */
    val languageTag: Flow<String?>

    /** 毛玻璃效果开关（默认开启；T6 设置页提供关闭项） */
    val blurEnabled: Flow<Boolean>

    /** 动态取色开关（Android 12+ 壁纸取色；默认关闭） */
    val dynamicColorEnabled: Flow<Boolean>

    /** 主题色 seed（ARGB Long；默认 [DEFAULT_SEED_COLOR]） */
    val seedColor: Flow<Long>

    /** OLED 纯黑开关（默认关闭） */
    val oledEnabled: Flow<Boolean>

    /** 高对比开关（无障碍，默认关闭） */
    val highContrastEnabled: Flow<Boolean>

    /** 圆角强度缩放（0.5–1.5，默认 [DEFAULT_CORNER_SCALE]） */
    val cornerScale: Flow<Float>

    /** 动画强度缩放（0.5–1.5，默认 [DEFAULT_MOTION_SCALE]） */
    val motionScale: Flow<Float>

    /** 图标风格（默认 [IconStyle.ROUNDED]，ui-design §5） */
    val iconStyle: Flow<IconStyle>

    /** 代码字体（默认 [CodeFont.MONO]） */
    val codeFont: Flow<CodeFont>

    /** 代码行号开关（默认开启） */
    val codeLineNumbers: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setBlurEnabled(enabled: Boolean)

    /** null 表示清除语言偏好（回退系统语言） */
    suspend fun setLanguageTag(tag: String?)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    suspend fun setSeedColor(color: Long)

    suspend fun setOledEnabled(enabled: Boolean)

    suspend fun setHighContrastEnabled(enabled: Boolean)

    suspend fun setCornerScale(scale: Float)

    suspend fun setMotionScale(scale: Float)

    suspend fun setIconStyle(style: IconStyle)

    suspend fun setCodeFont(font: CodeFont)

    suspend fun setCodeLineNumbers(enabled: Boolean)
}
