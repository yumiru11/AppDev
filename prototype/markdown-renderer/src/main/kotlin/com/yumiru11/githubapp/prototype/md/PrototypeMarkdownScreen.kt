// ===== PROTOTYPE（可抛弃，验证用）=====
// 组装浏览器：变体（Issue 正文 / 重型 GFM / README 式）× 亮/暗主题 × Material Symbols 图标条。
// 第二轮修复（用户反馈）：
// 1) blockQuote：alert 检测用 node.getUnescapedTextInNode() 切片；非 alert 复用官方 MarkdownBlockQuote
//    （逐子节点 MarkdownElement 解析 → 引用不再吞全文、checkbox/标注正常）
// 2) 行内代码：inlineCodeBackground 换不透明柔和表面色（0.38 为 annotator span，无法圆角——限制记录）
// 3) 样本扩容（多语言代码高亮矩阵）见 PrototypeSamples.kt
package com.yumiru11.githubapp.prototype.md

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Error
import com.composables.icons.materialsymbols.rounded.Home
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Lightbulb
import com.composables.icons.materialsymbols.rounded.Notifications
import com.composables.icons.materialsymbols.rounded.Priority_high
import com.composables.icons.materialsymbols.rounded.Search
import com.composables.icons.materialsymbols.rounded.Settings
import com.composables.icons.materialsymbols.rounded.Star
import com.composables.icons.materialsymbols.rounded.Warning
import com.composables.icons.materialsymbols.outlined.Home as OutlinedHome
import com.composables.icons.materialsymbols.outlined.Notifications as OutlinedNotifications
import com.composables.icons.materialsymbols.outlined.Search as OutlinedSearch
import com.composables.icons.materialsymbols.outlined.Settings as OutlinedSettings
import com.composables.icons.materialsymbols.outlined.Star as OutlinedStar
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.utils.getUnescapedTextInNode

@Composable
fun PrototypeMarkdownScreen(variant: MdVariant, darkTheme: Boolean) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "PROTOTYPE · ${variant.label} · ${if (darkTheme) "dark" else "light"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                IconStrip()
                Spacer(Modifier.height(12.dp))
                val state = rememberMarkdownState(
                    when (variant) {
                        MdVariant.A -> SAMPLE_A
                        MdVariant.B -> SAMPLE_B
                        MdVariant.C -> SAMPLE_C
                        MdVariant.D -> SAMPLE_D
                        MdVariant.E -> SAMPLE_E
                        MdVariant.F -> SAMPLE_F
                    },
                    immediate = true,
                )
                Markdown(
                    state,
                    imageTransformer = Coil3ImageTransformerImpl,
                    colors = markdownColor(
                        inlineCodeBackground = if (darkTheme) Color(0xFF2E2E34) else Color(0xFFE8E8EC),
                    ),
                    components = markdownComponents(
                        codeFence = { model ->
                            MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, _ ->
                                TextMateCodeBlock(code, language, darkTheme)
                            }
                        },
                        blockQuote = { model -> GitHubAlertOrQuote(model) },
                        checkbox = { model ->
                            com.mikepenz.markdown.m3.elements.MarkdownCheckBox(
                                content = model.content,
                                node = model.node,
                                style = model.typography.text,
                            )
                        },
                    ),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** GitHub Alert 检测 + 彩色卡片；非 Alert 复用官方 MarkdownBlockQuote（子节点解析、嵌套递归） */
private data class AlertSpec(
    val label: String,
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val icon: ImageVector,
)

/** 主题感知：用 M3 语义容器色（primaryContainer 等自动适配明暗），生产接 ExtendedColors */
@Composable
private fun alertSpecs(): Map<String, AlertSpec> {
    val c = MaterialTheme.colorScheme
    return mapOf(
        "NOTE" to AlertSpec(
            label = "Note",
            container = c.primaryContainer, onContainer = c.onPrimaryContainer,
            accent = c.primary, icon = MaterialSymbols.Rounded.Info,
        ),
        "TIP" to AlertSpec(
            label = "Tip",
            container = c.tertiaryContainer, onContainer = c.onTertiaryContainer,
            accent = c.tertiary, icon = MaterialSymbols.Rounded.Lightbulb,
        ),
        "IMPORTANT" to AlertSpec(
            label = "Important",
            container = c.secondaryContainer, onContainer = c.onSecondaryContainer,
            accent = c.secondary, icon = MaterialSymbols.Rounded.Priority_high,
        ),
        "WARNING" to AlertSpec(
            label = "Warning",
            container = c.surfaceContainerHighest, onContainer = c.onSurface,
            accent = Color(0xFFB26A00), icon = MaterialSymbols.Rounded.Warning,
        ),
        "CAUTION" to AlertSpec(
            label = "Caution",
            container = c.errorContainer, onContainer = c.onErrorContainer,
            accent = c.error, icon = MaterialSymbols.Rounded.Error,
        ),
    )
}

@Composable
private fun GitHubAlertOrQuote(model: MarkdownComponentModel) {
    val doc = model.content
    val nodeText = model.node.getUnescapedTextInNode(doc)
    val type = Regex("""(?m)^>?[ \t]*\[!([A-Z]+)\]""").find(nodeText)?.groupValues?.get(1)?.uppercase()
    val spec = alertSpecs()[type]
    if (spec != null) {
        val body = nodeText.lineSequence()
            .drop(1)
            .joinToString("\n") { line: String -> line.removePrefix(">").trimStart() }
            .trim()
        AlertCard(spec, body)
    } else {
        MarkdownBlockQuote(content = doc, node = model.node)
    }
}

@Composable
private fun AlertCard(spec: AlertSpec, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(spec.container, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(spec.icon, contentDescription = null, tint = spec.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = spec.label,
                color = spec.accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            // 递归解析告警正文（保留 **加粗**、`行内码` 等行内格式）
            Markdown(body)
        }
    }
}

/** 图标条：rounded（实心粗） vs outlined（细描边）两组对比，供用户选「圆角+细」偏好 */
@Composable
private fun IconStrip() {
    Column {
        IconGroup("Rounded（常规粗）", listOf(
            MaterialSymbols.Rounded.Star, MaterialSymbols.Rounded.Home,
            MaterialSymbols.Rounded.Search, MaterialSymbols.Rounded.Notifications, MaterialSymbols.Rounded.Settings,
        ))
        Spacer(Modifier.height(4.dp))
        IconGroup("Outlined（细描边，更轻）", listOf(
            MaterialSymbols.Outlined.OutlinedStar, MaterialSymbols.Outlined.OutlinedHome,
            MaterialSymbols.Outlined.OutlinedSearch, MaterialSymbols.Outlined.OutlinedNotifications, MaterialSymbols.Outlined.OutlinedSettings,
        ))
    }
}

@Composable
private fun IconGroup(label: String, icons: List<ImageVector>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(140.dp))
        icons.forEach { Icon(it, contentDescription = null, modifier = Modifier.size(30.dp)) }
    }
}
