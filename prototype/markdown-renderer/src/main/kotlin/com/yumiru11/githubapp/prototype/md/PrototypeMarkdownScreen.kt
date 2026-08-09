// ===== PROTOTYPE（可抛弃，验证用）=====
// 组装浏览器：变体（Issue 正文 / 重型 GFM / README 式）× 亮/暗主题 × Material Symbols 图标条。
// 本轮验证（用户第二轮反馈）：
// 1) 语法高亮：markdownComponents(codeFence/codeBlock = highlightedCodeFence/Block) 接入 -code 模块
// 2) GitHub Alert：0.38.1 无 alert 槽位 → 自定义 blockQuote 检测 `[!TYPE]` 渲染告警卡；非 Alert
//    引用块 → 普通 Material You 引用卡（容器色 + 左侧 3dp primary 条，plan §2.8）
// 3) 图标细观感：rounded（粗）vs outlined（细）分组标注对比
package com.yumiru11.githubapp.prototype.md

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Home as OutlinedHome
import com.composables.icons.materialsymbols.outlined.Notifications as OutlinedNotifications
import com.composables.icons.materialsymbols.outlined.Search as OutlinedSearch
import com.composables.icons.materialsymbols.outlined.Settings as OutlinedSettings
import com.composables.icons.materialsymbols.outlined.Star as OutlinedStar
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
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
fun PrototypeMarkdownScreen(variant: MdVariant, darkTheme: Boolean) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "PROTOTYPE - ${variant.label} - ${if (darkTheme) "dark" else "light"}",
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
                    },
                )
                Markdown(
                    state,
                    imageTransformer = Coil3ImageTransformerImpl,
                    components = markdownComponents(
                        codeFence = highlightedCodeFence,
                        codeBlock = highlightedCodeBlock,
                        blockQuote = { model -> GitHubAlertOrQuote(model) },
                    ),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** GitHub Alert 检测 + 渲染；非 Alert 引用块用 Material You 引用卡 */
@Composable
private fun GitHubAlertOrQuote(model: MarkdownComponentModel) {
    val raw = model.content.trimStart()
    val match = Regex("""\[!([A-Z]+)\]""").find(raw)
    val type = match?.groupValues?.get(1)?.uppercase()
    if (type != null) {
        val body = raw.removePrefix("[!$type]").trimStart().removePrefix("]").trimStart()
        AlertCard(type, body)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Box(modifier = Modifier.width(3.dp).height(28.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(
            text = raw,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private data class AlertStyles(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val icon: ImageVector,
)

@Composable
private fun alertStyles(type: String): AlertStyles {
    val c = MaterialTheme.colorScheme
    return when (type) {
        "NOTE" -> AlertStyles(c.primary, c.primaryContainer, c.onPrimaryContainer, MaterialSymbols.Rounded.Info)
        "TIP" -> AlertStyles(c.tertiary, c.tertiaryContainer, c.onTertiaryContainer, MaterialSymbols.Rounded.Lightbulb)
        "IMPORTANT" -> AlertStyles(c.secondary, c.secondaryContainer, c.onSecondaryContainer, MaterialSymbols.Rounded.Priority_high)
        "WARNING" -> AlertStyles(Color(0xFF9A5B00), c.surfaceContainerHighest, c.onSurface, MaterialSymbols.Rounded.Warning)
        else -> AlertStyles(c.error, c.errorContainer, c.onErrorContainer, MaterialSymbols.Rounded.Error)
    }
}

@Composable
private fun AlertCard(type: String, body: String) {
    val s = alertStyles(type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(s.container, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(s.icon, contentDescription = null, tint = s.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = type,
                color = s.accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = body, color = s.onContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 图标条：rounded（粗）与 outlined（细）分组对比 */
@Composable
private fun IconStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IconGroup("Rounded (thick)", listOf(
            MaterialSymbols.Rounded.Star,
            MaterialSymbols.Rounded.Home,
            MaterialSymbols.Rounded.Search,
            MaterialSymbols.Rounded.Notifications,
            MaterialSymbols.Rounded.Settings,
        ))
        IconGroup("Outlined (finer)", listOf(
            MaterialSymbols.Outlined.OutlinedStar,
            MaterialSymbols.Outlined.OutlinedHome,
            MaterialSymbols.Outlined.OutlinedSearch,
            MaterialSymbols.Outlined.OutlinedNotifications,
            MaterialSymbols.Outlined.OutlinedSettings,
        ))
    }
}

@Composable
private fun IconGroup(label: String, icons: List<ImageVector>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(130.dp),
        )
        icons.forEach { Icon(it, contentDescription = null, modifier = Modifier.size(30.dp)) }
    }
}