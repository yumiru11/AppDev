package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Material You 主题令牌（CSS 变量映射载体）。
 *
 * 从 Compose 的 [ColorScheme] 提取关键色，注入 WebView 的 `:root` CSS 变量
 * （plan.md §2.10）。明暗主题切换时重新生成注入即生效。
 *
 * 自维护 CSS 令牌体系，**不照搬 GitHub 蓝灰**——颜色取自 Material 3 色板语义。
 *
 * @property primary 主色（链接色）
 * @property onSurface 正文文字色
 * @property surface 背景色
 * @property surfaceContainerLow 低容器背景（代码块/引用块）
 * @property surfaceContainerHigh 高容器背景（hover/选中）
 * @property outlineVariant 边框色（表格边线）
 * @property isDark 是否深色主题（决定代码块默认主题等）
 */
data class MarkdownThemeTokens(
    val primary: String,
    val onSurface: String,
    val surface: String,
    val surfaceContainerLow: String,
    val surfaceContainerHigh: String,
    val outlineVariant: String,
    val isDark: Boolean,
) {
    /**
     * 生成 `:root { ... }` CSS 变量声明块（用于注入 `<style id="theme-vars">`）。
     *
     * 仅含色板变量；字体与圆角常量由 [markdown-you.css] 直接声明（静态）。
     */
    fun toCssVariables(): String =
        buildString {
            append(":root {\n")
            append("  --md-sys-color-primary: $primary;\n")
            append("  --md-sys-color-on-surface: $onSurface;\n")
            append("  --md-sys-color-surface: $surface;\n")
            append("  --md-sys-color-surface-container-low: $surfaceContainerLow;\n")
            append("  --md-sys-color-surface-container-high: $surfaceContainerHigh;\n")
            append("  --md-sys-color-outline-variant: $outlineVariant;\n")
            append("}\n")
        }

    companion object {
        /** 从 Compose [ColorScheme] 派生令牌（明暗态由 [isDark] 决定） */
        fun fromColorScheme(
            scheme: ColorScheme,
            isDark: Boolean,
        ): MarkdownThemeTokens =
            MarkdownThemeTokens(
                primary = toHex(scheme.primary),
                onSurface = toHex(scheme.onSurface),
                surface = toHex(scheme.surface),
                surfaceContainerLow = toHex(scheme.surfaceContainerLow),
                surfaceContainerHigh = toHex(scheme.surfaceContainerHigh),
                outlineVariant = toHex(scheme.outlineVariant),
                isDark = isDark,
            )

        /** 浅色主题默认令牌（Material 3 lightColorScheme 派生） */
        fun fromLightScheme(): MarkdownThemeTokens = fromColorScheme(lightColorScheme(), isDark = false)

        /** 深色主题默认令牌（Material 3 darkColorScheme 派生） */
        fun fromDarkScheme(): MarkdownThemeTokens = fromColorScheme(darkColorScheme(), isDark = true)

        /**
         * 主题版本哈希（plan.md §2.10：主题变更时缓存失效）。
         *
         * 基于 [fromLightScheme] / [fromDarkScheme] 的令牌字段拼接后取 hashCode，
         * 确保明暗切换或令牌值变化时版本号变化 → 缓存自动失效。
         */
        fun versionHash(): String {
            val light = fromLightScheme()
            val dark = fromDarkScheme()
            val combined = light.primary + light.onSurface + light.surface + dark.primary + dark.onSurface + dark.surface
            return "th-${combined.hashCode()}"
        }

        /** Color → #RRGGBB hex 字符串（丢弃 alpha，WebView 背景透明由容器承载） */
        private fun toHex(color: Color): String {
            val r = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
            return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
        }
    }
}
