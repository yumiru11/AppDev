package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Material You → WebView CSS variable mapper（plan.md §2.10 / docs/research/webview-material-you-fusion.md）。
 *
 * 纯函数：`ColorScheme + isDark → CSS 变量声明块 / document-start 注入脚本`。
 *
 * 设计：
 * - 容器与前景语义色随 MaterialTheme（[ColorScheme]），实现主题融合
 * - `--color-prettylights-syntax-*` 保留 GitHub 原色（半融合 C 方案），不随 M3 变化
 * - 注入脚本只包含本对象生成的 hex/theme 值，**token 绝不进入 WebView JS 上下文**
 */
object MaterialYouFusionMapper {
    private const val THEME_LIGHT = "light"
    private const val THEME_DARK = "dark"

    /** 圆形令牌：与 AppDimens 一致（不引入 designsystem 依赖，静态 CSS 常量）。 */
    private const val CORNER_SMALL = "8px"
    private const val CORNER_MEDIUM = "12px"
    private const val CORNER_LARGE = "16px"

    /** GitHub PrettyLights 原始语法色（light，github-markdown-css 5.9.0 提取）。 */
    private val GITHUB_SYNTAX_LIGHT: Map<String, String> =
        mapOf(
            "color-prettylights-syntax-brackethighlighter-angle" to "#59636e",
            "color-prettylights-syntax-brackethighlighter-unmatched" to "#82071e",
            "color-prettylights-syntax-carriage-return-bg" to "#cf222e",
            "color-prettylights-syntax-carriage-return-text" to "#f6f8fa",
            "color-prettylights-syntax-comment" to "#59636e",
            "color-prettylights-syntax-constant" to "#0550ae",
            "color-prettylights-syntax-constant-other-reference-link" to "#0a3069",
            "color-prettylights-syntax-entity" to "#6639ba",
            "color-prettylights-syntax-entity-tag" to "#0550ae",
            "color-prettylights-syntax-keyword" to "#cf222e",
            "color-prettylights-syntax-markup-changed-bg" to "#ffd8b5",
            "color-prettylights-syntax-markup-changed-text" to "#953800",
            "color-prettylights-syntax-markup-deleted-bg" to "#ffebe9",
            "color-prettylights-syntax-markup-deleted-text" to "#82071e",
            "color-prettylights-syntax-markup-heading" to "#0550ae",
            "color-prettylights-syntax-markup-ignored-bg" to "#0550ae",
            "color-prettylights-syntax-markup-ignored-text" to "#d1d9e0",
            "color-prettylights-syntax-markup-inserted-bg" to "#dafbe1",
            "color-prettylights-syntax-markup-inserted-text" to "#116329",
            "color-prettylights-syntax-markup-list" to "#3b2300",
            "color-prettylights-syntax-meta-diff-range" to "#8250df",
            "color-prettylights-syntax-string" to "#0a3069",
            "color-prettylights-syntax-string-regexp" to "#116329",
            "color-prettylights-syntax-sublimelinter-gutter-mark" to "#818b98",
            "color-prettylights-syntax-variable" to "#953800",
            "color-prettylights-syntax-markup-bold" to "#1f2328",
            "color-prettylights-syntax-markup-italic" to "#1f2328",
            "color-prettylights-syntax-storage-modifier-import" to "#1f2328",
        )

    /** GitHub PrettyLights 原始语法色（dark，github-markdown-css 5.9.0 提取）。 */
    private val GITHUB_SYNTAX_DARK: Map<String, String> =
        mapOf(
            "color-prettylights-syntax-brackethighlighter-angle" to "#9198a1",
            "color-prettylights-syntax-brackethighlighter-unmatched" to "#f85149",
            "color-prettylights-syntax-carriage-return-bg" to "#b62324",
            "color-prettylights-syntax-carriage-return-text" to "#f0f6fc",
            "color-prettylights-syntax-comment" to "#9198a1",
            "color-prettylights-syntax-constant" to "#79c0ff",
            "color-prettylights-syntax-constant-other-reference-link" to "#a5d6ff",
            "color-prettylights-syntax-entity" to "#d2a8ff",
            "color-prettylights-syntax-entity-tag" to "#7ee787",
            "color-prettylights-syntax-keyword" to "#ff7b72",
            "color-prettylights-syntax-markup-bold" to "#f0f6fc",
            "color-prettylights-syntax-markup-changed-bg" to "#5a1e02",
            "color-prettylights-syntax-markup-changed-text" to "#ffdfb6",
            "color-prettylights-syntax-markup-deleted-bg" to "#67060c",
            "color-prettylights-syntax-markup-deleted-text" to "#ffdcd7",
            "color-prettylights-syntax-markup-heading" to "#1f6feb",
            "color-prettylights-syntax-markup-ignored-bg" to "#1158c7",
            "color-prettylights-syntax-markup-ignored-text" to "#f0f6fc",
            "color-prettylights-syntax-markup-inserted-bg" to "#033a16",
            "color-prettylights-syntax-markup-inserted-text" to "#aff5b4",
            "color-prettylights-syntax-markup-italic" to "#f0f6fc",
            "color-prettylights-syntax-markup-list" to "#f2cc60",
            "color-prettylights-syntax-meta-diff-range" to "#d2a8ff",
            "color-prettylights-syntax-storage-modifier-import" to "#f0f6fc",
            "color-prettylights-syntax-string" to "#a5d6ff",
            "color-prettylights-syntax-string-regexp" to "#7ee787",
            "color-prettylights-syntax-sublimelinter-gutter-mark" to "#3d444d",
            "color-prettylights-syntax-variable" to "#ffa657",
        )

    /**
     * 生成完整 CSS 变量声明块。
     *
     * 选择器覆盖 `:root` / `.markdown-body` / `[data-theme]` 三处，且必须放在
     * github-markdown.css 之后注入，保证同特异性下后声明者胜。
     */
    fun buildCss(
        scheme: ColorScheme,
        isDark: Boolean,
    ): String =
        buildString {
            val theme = themeName(isDark)
            append(":root,\n")
            append(".markdown-body,\n")
            append("[data-theme=\"$theme\"] {\n")
            append(declarations(scheme, isDark))
            append("}\n")
        }

    /**
     * 生成 androidx.webkit `addDocumentStartJavaScript` 注入脚本。
     *
     * 只注入 `data-theme` 与 hex 颜色值；无 token、无 HTML 标签、无外部输入拼接面。
     */
    fun buildStartScript(
        scheme: ColorScheme,
        isDark: Boolean,
    ): String =
        buildString {
            append("(function(){try{")
            append("var e=document.documentElement;")
            append("if(!e)return;")
            append("e.setAttribute('data-theme','")
            append(themeName(isDark))
            append("');")
            colorDeclarations(scheme, isDark).forEach { (name, value) ->
                append("e.style.setProperty('")
                append(name)
                append("','")
                append(value)
                append("');")
            }
            append("}catch(_){}})();")
        }

    /** CSS 变量全量声明（文本样式，供 buildCss 使用）。 */
    private fun declarations(
        scheme: ColorScheme,
        isDark: Boolean,
    ): String =
        buildString {
            allDeclarations(scheme, isDark).forEach { (name, value) ->
                append("  ")
                append(name)
                append(": ")
                append(value)
                append(";\n")
            }
        }

    /** 颜色变量声明（仅供 start script 使用；字体/圆角不注入 JS）。 */
    private fun colorDeclarations(
        scheme: ColorScheme,
        isDark: Boolean,
    ): Map<String, String> = materialRoleDeclarations(scheme) + githubSemanticDeclarations(scheme) + syntaxDeclarations(isDark)

    private fun allDeclarations(
        scheme: ColorScheme,
        isDark: Boolean,
    ): Map<String, String> =
        colorDeclarations(scheme, isDark) +
            mapOf(
                "--fontStack-sansSerif" to
                    "Roboto, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif",
                "--fontStack-monospace" to "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace",
                "--control-borderRadius" to CORNER_SMALL,
                "--borderRadius-small" to CORNER_SMALL,
                "--borderRadius-medium" to CORNER_MEDIUM,
                "--borderRadius-large" to CORNER_LARGE,
                "--md-sys-shape-corner-small" to CORNER_SMALL,
                "--md-sys-shape-corner-medium" to CORNER_MEDIUM,
                "--md-sys-shape-corner-large" to CORNER_LARGE,
            )

    private fun materialRoleDeclarations(scheme: ColorScheme): Map<String, String> =
        mapOf(
            "--md-sys-color-primary" to cssColor(scheme.primary),
            "--md-sys-color-on-primary" to cssColor(scheme.onPrimary),
            "--md-sys-color-primary-container" to cssColor(scheme.primaryContainer),
            "--md-sys-color-on-primary-container" to cssColor(scheme.onPrimaryContainer),
            "--md-sys-color-secondary" to cssColor(scheme.secondary),
            "--md-sys-color-on-secondary" to cssColor(scheme.onSecondary),
            "--md-sys-color-secondary-container" to cssColor(scheme.secondaryContainer),
            "--md-sys-color-on-secondary-container" to cssColor(scheme.onSecondaryContainer),
            "--md-sys-color-tertiary" to cssColor(scheme.tertiary),
            "--md-sys-color-on-tertiary" to cssColor(scheme.onTertiary),
            "--md-sys-color-tertiary-container" to cssColor(scheme.tertiaryContainer),
            "--md-sys-color-on-tertiary-container" to cssColor(scheme.onTertiaryContainer),
            "--md-sys-color-error" to cssColor(scheme.error),
            "--md-sys-color-on-error" to cssColor(scheme.onError),
            "--md-sys-color-error-container" to cssColor(scheme.errorContainer),
            "--md-sys-color-on-error-container" to cssColor(scheme.onErrorContainer),
            "--md-sys-color-background" to cssColor(scheme.background),
            "--md-sys-color-on-background" to cssColor(scheme.onBackground),
            "--md-sys-color-surface" to cssColor(scheme.surface),
            "--md-sys-color-on-surface" to cssColor(scheme.onSurface),
            "--md-sys-color-surface-variant" to cssColor(scheme.surfaceVariant),
            "--md-sys-color-on-surface-variant" to cssColor(scheme.onSurfaceVariant),
            "--md-sys-color-surface-tint" to cssColor(scheme.surfaceTint),
            "--md-sys-color-inverse-surface" to cssColor(scheme.inverseSurface),
            "--md-sys-color-inverse-on-surface" to cssColor(scheme.inverseOnSurface),
            "--md-sys-color-outline" to cssColor(scheme.outline),
            "--md-sys-color-outline-variant" to cssColor(scheme.outlineVariant),
            "--md-sys-color-scrim" to cssColor(scheme.scrim),
            "--md-sys-color-surface-bright" to cssColor(scheme.surfaceBright),
            "--md-sys-color-surface-dim" to cssColor(scheme.surfaceDim),
            "--md-sys-color-surface-container" to cssColor(scheme.surfaceContainer),
            "--md-sys-color-surface-container-low" to cssColor(scheme.surfaceContainerLow),
            "--md-sys-color-surface-container-lowest" to cssColor(scheme.surfaceContainerLowest),
            "--md-sys-color-surface-container-high" to cssColor(scheme.surfaceContainerHigh),
            "--md-sys-color-surface-container-highest" to cssColor(scheme.surfaceContainerHighest),
        )

    private fun githubSemanticDeclarations(scheme: ColorScheme): Map<String, String> =
        mapOf(
            "--fgColor-default" to cssColor(scheme.onSurface),
            "--fgColor-muted" to cssColor(scheme.onSurfaceVariant),
            "--fgColor-accent" to cssColor(scheme.primary),
            "--fgColor-success" to cssColor(scheme.tertiary),
            "--fgColor-attention" to cssColor(scheme.secondary),
            "--fgColor-done" to cssColor(scheme.secondary),
            "--fgColor-danger" to cssColor(scheme.error),
            "--bgColor-default" to cssColor(scheme.surface),
            "--bgColor-muted" to cssColor(scheme.surfaceContainerLow),
            "--bgColor-inset" to cssColor(scheme.surfaceContainerLowest),
            "--bgColor-neutral-muted" to cssColor(scheme.surfaceContainerHigh),
            "--bgColor-attention-muted" to cssColor(scheme.surfaceContainerHigh),
            "--bgColor-danger-muted" to cssColor(scheme.errorContainer),
            "--borderColor-default" to cssColor(scheme.outlineVariant),
            "--borderColor-muted" to cssColor(scheme.outlineVariant),
            "--borderColor-neutral-muted" to cssColor(scheme.outlineVariant),
            "--borderColor-accent-emphasis" to cssColor(scheme.primary),
            "--borderColor-success-emphasis" to cssColor(scheme.tertiary),
            "--borderColor-attention-emphasis" to cssColor(scheme.secondary),
            "--borderColor-done-emphasis" to cssColor(scheme.secondary),
            "--borderColor-danger-emphasis" to cssColor(scheme.error),
            "--focus-outlineColor" to cssColor(scheme.primary),
            "--color-prettylights-syntax-invalid-illegal-bg" to cssColor(scheme.errorContainer),
            "--color-prettylights-syntax-invalid-illegal-text" to cssColor(scheme.error),
        )

    private fun syntaxDeclarations(isDark: Boolean): Map<String, String> =
        (if (isDark) GITHUB_SYNTAX_DARK else GITHUB_SYNTAX_LIGHT).mapKeys { "--${it.key}" }

    private fun themeName(isDark: Boolean): String = if (isDark) THEME_DARK else THEME_LIGHT

    /** Color → #RRGGBB；带 alpha 时输出 rgba()，保证注入值不含任何用户输入。 */
    private fun cssColor(color: Color): String {
        val r = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        val a = color.alpha
        return if (a >= 0.999f) {
            String.format(Locale.US, "#%02X%02X%02X", r, g, b)
        } else {
            String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", r, g, b, a)
        }
    }
}
