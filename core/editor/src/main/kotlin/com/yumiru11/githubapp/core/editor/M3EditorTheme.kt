@file:Suppress("MatchingDeclarationName") // M3 编辑器主题工具集（令牌+映射+JSON 构建一体），拆文件降低内聚（同 core:markdown M3TextMateTheme 先例）

package com.yumiru11.githubapp.core.editor

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.yumiru11.githubapp.core.designsystem.theme.ExtendedColors
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors

/**
 * M3 派生的编辑器主题（plan.md §8.2 映射表）。
 *
 * Sora Editor 的 TextMate 着色由 VS Code 主题 JSON 驱动（tokenColors = 语法色、
 * colors = 编辑器 chrome 色），因此把 M3 语义色角色序列化为主题 JSON 注入，
 * 保证编辑器随 Material You 主题（动态取色/深色/OLED/高对比）走同一套设计系统——
 * 不照搬 IDE 主题。
 *
 * 映射表（与 plan.md §8.2 逐条一致）：
 * ```
 * editor bg      = surfaceContainerLow
 * text           = onSurface
 * line number    = onSurfaceVariant
 * selection      = primaryContainer
 * current line   = surfaceContainerHigh
 * keyword        = tertiary
 * string         = success
 * comment        = onSurfaceVariant
 * function       = primary
 * number         = secondary
 * ```
 */

data class EditorThemeTokens(
    val background: Color,
    val text: Color,
    val lineNumber: Color,
    val selection: Color,
    val currentLine: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val function: Color,
    val number: Color,
)

/** 由 [ColorScheme] + [ExtendedColors] 计算编辑器令牌（纯函数，供测试直接验证映射）。 */
fun m3EditorThemeTokens(
    colorScheme: ColorScheme,
    extendedColors: ExtendedColors,
): EditorThemeTokens =
    EditorThemeTokens(
        background = colorScheme.surfaceContainerLow,
        text = colorScheme.onSurface,
        lineNumber = colorScheme.onSurfaceVariant,
        selection = colorScheme.primaryContainer,
        currentLine = colorScheme.surfaceContainerHigh,
        keyword = colorScheme.tertiary,
        string = extendedColors.success,
        comment = colorScheme.onSurfaceVariant,
        function = colorScheme.primary,
        number = colorScheme.secondary,
    )

/** 从当前 [MaterialTheme] 缓存编辑器令牌（随主题变化重建）。 */
@Composable
fun rememberM3EditorThemeTokens(): EditorThemeTokens {
    val scheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    return remember(scheme, extended) {
        m3EditorThemeTokens(colorScheme = scheme, extendedColors = extended)
    }
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** 一条 scope 选择器 → 编辑器令牌色 的映射规则。 */
internal data class TokenScopeRule(
    val scope: String,
    val color: (EditorThemeTokens) -> Color,
)

/**
 * scope 选择器列表（general → specific，越具体越靠后；tm4e 同 VS Code「最后命中者胜」）。
 * 语法角色仅覆盖 plan.md §8.2 的 5 类（keyword/string/comment/function/number），
 * 各语言语法的细分 scope 归并到对应角色。
 */
internal val TOKEN_SCOPE_RULES: List<TokenScopeRule> =
    listOf(
        TokenScopeRule("comment") { it.comment },
        TokenScopeRule("keyword") { it.keyword },
        TokenScopeRule("storage.type") { it.keyword },
        TokenScopeRule("storage.modifier") { it.keyword },
        TokenScopeRule("string") { it.string },
        TokenScopeRule("constant.numeric") { it.number },
        TokenScopeRule("entity.name.function") { it.function },
        TokenScopeRule("support.function") { it.function },
    )

/** 由 [tokens] 序列化 VS Code 主题 JSON（colors 段 = chrome 色，tokenColors 段 = 语法色）。 */
internal fun buildEditorThemeJson(
    name: String,
    tokens: EditorThemeTokens,
): String {
    val sb = StringBuilder()
    sb.append("{\n")
    sb.append("\"name\": \"").append(name).append("\",\n")
    sb.append("\"colors\": {\n")
    sb.append("\"editor.background\": \"").append(tokens.background.toHex()).append("\",\n")
    sb.append("\"editor.foreground\": \"").append(tokens.text.toHex()).append("\",\n")
    sb.append("\"editorLineNumber.foreground\": \"").append(tokens.lineNumber.toHex()).append("\",\n")
    sb.append("\"editor.selectionBackground\": \"").append(tokens.selection.toHex()).append("\",\n")
    sb.append("\"editor.lineHighlightBackground\": \"").append(tokens.currentLine.toHex()).append("\"\n")
    sb.append("},\n")
    sb.append("\"tokenColors\": [\n")
    TOKEN_SCOPE_RULES.forEachIndexed { index, rule ->
        sb.append("  { \"scope\": \"").append(rule.scope).append("\", ")
        sb.append("\"settings\": { \"foreground\": \"").append(rule.color(tokens).toHex()).append("\" } }")
        if (index != TOKEN_SCOPE_RULES.lastIndex) sb.append(",")
        sb.append("\n")
    }
    sb.append("]\n")
    sb.append("}")
    return sb.toString()
}
