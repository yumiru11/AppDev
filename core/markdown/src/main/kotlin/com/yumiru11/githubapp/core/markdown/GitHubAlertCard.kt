package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Error
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Lightbulb
import com.composables.icons.materialsymbols.rounded.Priority_high
import com.composables.icons.materialsymbols.rounded.Warning
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.utils.getUnescapedTextInNode

/** GitHub Alert 规格：标签、容器色、前景色、强调色、图标 */
private data class AlertSpec(
    val label: String,
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val icon: ImageVector,
)

/** 主题感知：用 M3 语义容器色（primaryContainer 等自动适配明暗） */
@Composable
private fun alertSpecs(): Map<String, AlertSpec> {
    val c = MaterialTheme.colorScheme
    return mapOf(
        "NOTE" to AlertSpec(
            label = stringResource(R.string.alert_note),
            container = c.primaryContainer,
            onContainer = c.onPrimaryContainer,
            accent = c.primary,
            icon = MaterialSymbols.Rounded.Info,
        ),
        "TIP" to AlertSpec(
            label = stringResource(R.string.alert_tip),
            container = c.tertiaryContainer,
            onContainer = c.onTertiaryContainer,
            accent = c.tertiary,
            icon = MaterialSymbols.Rounded.Lightbulb,
        ),
        "IMPORTANT" to AlertSpec(
            label = stringResource(R.string.alert_important),
            container = c.secondaryContainer,
            onContainer = c.onSecondaryContainer,
            accent = c.secondary,
            icon = MaterialSymbols.Rounded.Priority_high,
        ),
        // 设计系统暂无 warning 语义色（T6 未落地），用 error 近似 GitHub 的橙色警示
        "WARNING" to AlertSpec(
            label = stringResource(R.string.alert_warning),
            container = c.surfaceContainerHighest,
            onContainer = c.onSurface,
            accent = c.error,
            icon = MaterialSymbols.Rounded.Warning,
        ),
        "CAUTION" to AlertSpec(
            label = stringResource(R.string.alert_caution),
            container = c.errorContainer,
            onContainer = c.onErrorContainer,
            accent = c.error,
            icon = MaterialSymbols.Rounded.Error,
        ),
    )
}

/**
 * GitHub Alert 检测 + 彩色卡片；非 Alert 复用官方 MarkdownBlockQuote。
 *
 * 检测逻辑：匹配 `> [!TYPE]` 模式，TYPE 为 NOTE/TIP/IMPORTANT/WARNING/CAUTION。
 * Alert 正文递归解析（保留 **加粗**、`行内码` 等行内格式）。
 */
@Composable
fun GitHubAlertOrQuote(model: MarkdownComponentModel) {
    val doc = model.content
    val nodeText = model.node.getUnescapedTextInNode(doc)
    val type = Regex("""(?m)^>?[ \t]*\[!([A-Z]+)\]""")
        .find(nodeText)
        ?.groupValues
        ?.get(1)
        ?.uppercase()
    val spec = alertSpecs()[type]
    if (spec != null) {
        val body = nodeText.lineSequence()
            .drop(1)
            .joinToString("\n") { line -> line.removePrefix(">").trimStart() }
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
        androidx.compose.foundation.layout.Column {
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
