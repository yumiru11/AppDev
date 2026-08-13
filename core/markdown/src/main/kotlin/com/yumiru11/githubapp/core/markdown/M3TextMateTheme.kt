@file:Suppress("MatchingDeclarationName") // M3 主题工具集（映射表+解析+构建一体），拆文件降低内聚

package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import java.io.ByteArrayInputStream

/*
 * M3 派生的 TextMate 主题。
 *
 * 用 Material You [ColorScheme] 语义色角色动态生成代码高亮配色，替代 VS Code 固定调色板，
 * 保证代码块随 App 主题（动态取色/深色/OLED/高对比）走同一套设计系统。
 *
 * 角色映射覆盖 7+ 个可辨识视觉层级（keyword→primary、type→secondary、function→tertiary、
 * string/constant→onPrimaryContainer、variable→onSurfaceVariant、comment→outline、
 * punctuation/operator→outlineVariant、invalid/deleted→error 等）。
 *
 * textmate-core 的 [Theme] 构造器为 internal，只能经公开 API [ThemeReader.readTheme] 从主题
 * JSON 构建，因此这里把 M3 配色序列化为主题 JSON。
 */

/** 一条 scope 选择器 → M3 角色 的映射规则。 */
internal data class ScopeRule(
    val scope: String,
    val fontStyle: String? = null,
    val role: (ColorScheme) -> Color,
)

/**
 * scope 选择器列表（general → specific，越具体越靠后）。
 *
 * 与 textmate 的 [Theme.match] 一致采用「最后命中者胜」：`keyword.operator` 必须排在
 * `keyword` 之后，否则 operator token 会被 keyword 规则覆盖。
 */
internal val SCOPE_RULES: List<ScopeRule> =
    listOf(
        ScopeRule("constant") { it.onPrimaryContainer },
        ScopeRule("string") { it.onPrimaryContainer },
        ScopeRule("keyword") { it.primary },
        ScopeRule("keyword.operator") { it.outlineVariant },
        ScopeRule("storage.modifier") { it.primary },
        ScopeRule("storage.type") { it.secondary },
        ScopeRule("entity.name.type") { it.secondary },
        ScopeRule("support.type") { it.secondary },
        ScopeRule("support.class") { it.secondary },
        ScopeRule("entity.name.namespace") { it.secondary },
        ScopeRule("entity.other.attribute-name") { it.secondary },
        ScopeRule("entity.name.function") { it.tertiary },
        ScopeRule("support.function") { it.tertiary },
        ScopeRule("entity.name.method") { it.tertiary },
        ScopeRule("meta.function") { it.tertiary },
        ScopeRule("variable") { it.onSurfaceVariant },
        ScopeRule("variable.language") { it.onSecondaryContainer },
        ScopeRule("variable.parameter") { it.onSecondaryContainer },
        ScopeRule("comment") { it.outline },
        ScopeRule("punctuation") { it.outlineVariant },
        ScopeRule("markup.heading") { it.primary },
        ScopeRule("markup.underline", role = { it.onSurfaceVariant }, fontStyle = "underline"),
        ScopeRule("markup.bold", role = { it.primary }, fontStyle = "bold"),
        ScopeRule("markup.italic", role = { it.tertiary }, fontStyle = "italic"),
        ScopeRule("markup.inserted") { it.onTertiaryContainer },
        ScopeRule("markup.changed") { it.onTertiaryContainer },
        ScopeRule("markup.deleted") { it.error },
        ScopeRule("invalid") { it.error },
    )

/** 与 textmate `matchesScope` 一致的点分段前缀匹配。 */
private fun matchesScopeSelector(
    scope: String,
    pattern: String,
): Boolean =
    scope == pattern ||
        (scope.startsWith(pattern) && scope.length > pattern.length && scope[pattern.length] == '.')

/**
 * 解析给定 scope 在 [scheme] 下的 M3 前景色（最后命中者胜，存在文本时兜底 [ColorScheme.onSurface]）。
 * 纯函数，供测试与主题构造共用同一份映射。
 */
fun resolveScopeColor(
    scope: String,
    scheme: ColorScheme,
): Color {
    var result: Color = scheme.onSurface
    for (rule in SCOPE_RULES) {
        if (matchesScopeSelector(scope, rule.scope)) {
            result = rule.role(scheme)
        }
    }
    return result
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** 由 [scheme] 序列化主题 JSON（默认样式取 editor.foreground/background）。 */
internal fun buildThemeJson(
    name: String,
    scheme: ColorScheme,
): String {
    val sb = StringBuilder()
    sb.append("{\n")
    sb.append("\"name\": \"$name\",\n")
    sb.append("\"colors\": {\n")
    sb.append("\"editor.foreground\": \"").append(scheme.onSurface.toHex()).append("\",\n")
    // 透明背景，交给 CodeBlock 容器（避免双背景）
    sb.append("\"editor.background\": \"#00000000\"\n")
    sb.append("},\n")
    sb.append("\"tokenColors\": [\n")
    SCOPE_RULES.forEachIndexed { index, rule ->
        sb.append("  { \"scope\": \"").append(rule.scope).append("\", ")
        sb
            .append("\"settings\": { \"foreground\": \"")
            .append(rule.role(scheme).toHex())
            .append("\"")
        if (rule.fontStyle != null) {
            sb.append(", \"fontStyle\": \"").append(rule.fontStyle).append("\"")
        }
        sb.append(" } }")
        if (index != SCOPE_RULES.lastIndex) sb.append(",")
        sb.append("\n")
    }
    sb.append("]\n")
    sb.append("}")
    return sb.toString()
}

/** 由 [scheme] 构建 M3 派生的 TextMate 主题。 */
fun buildM3Theme(
    name: String,
    scheme: ColorScheme,
): Theme {
    val json = buildThemeJson(name, scheme)
    return ThemeReader.readTheme(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
}

/** 从当前 [MaterialTheme.colorScheme] 缓存 M3 TextMate 主题（随主题变化重建）。 */
@Composable
fun rememberM3TextMateTheme(darkTheme: Boolean): Theme {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme, darkTheme) {
        buildM3Theme(name = if (darkTheme) "M3 Dark" else "M3 Light", scheme = scheme)
    }
}
