package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors
import com.yumiru11.githubapp.core.markdown.native.GitHubAlertParser
import com.yumiru11.githubapp.core.markdown.native.GitHubAlertType
import com.yumiru11.githubapp.core.markdown.native.ParsedGitHubAlert

/** GitHub Alert 规格：本地化标签、语义容器色、前景色、Octicons 矢量图标。 */
private data class AlertSpec(
    val label: String,
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val icon: ImageVector,
)

/** 主题感知：ExtendedColors 提供 NOTE/TIP/IMPORTANT/WARNING/CAUTION 深浅两套语义色。 */
@Composable
private fun alertSpec(
    label: String,
    parsed: ParsedGitHubAlert,
): AlertSpec {
    val extended = MaterialTheme.extendedColors
    return when (parsed.type) {
        GitHubAlertType.NOTE -> {
            AlertSpec(
                label = label,
                container = extended.noteContainer,
                onContainer = extended.onNoteContainer,
                accent = extended.onNoteContainer,
                icon = AppDevOcticons.Info,
            )
        }

        GitHubAlertType.TIP -> {
            AlertSpec(
                label = label,
                container = extended.tipContainer,
                onContainer = extended.onTipContainer,
                accent = extended.onTipContainer,
                icon = AppDevOcticons.LightBulb,
            )
        }

        GitHubAlertType.IMPORTANT -> {
            AlertSpec(
                label = label,
                container = extended.importantContainer,
                onContainer = extended.onImportantContainer,
                accent = extended.onImportantContainer,
                icon = AppDevOcticons.Alert,
            )
        }

        GitHubAlertType.WARNING -> {
            AlertSpec(
                label = label,
                container = extended.warningContainer,
                onContainer = extended.onWarningContainer,
                accent = extended.onWarningContainer,
                icon = AppDevOcticons.Stop,
            )
        }

        GitHubAlertType.CAUTION -> {
            AlertSpec(
                label = label,
                container = extended.cautionContainer,
                onContainer = extended.onCautionContainer,
                accent = extended.onCautionContainer,
                icon = AppDevOcticons.Flame,
            )
        }
    }
}

@Composable
private fun alertLabel(type: GitHubAlertType): String =
    when (type) {
        GitHubAlertType.NOTE -> stringResource(R.string.alert_note)
        GitHubAlertType.TIP -> stringResource(R.string.alert_tip)
        GitHubAlertType.IMPORTANT -> stringResource(R.string.alert_important)
        GitHubAlertType.WARNING -> stringResource(R.string.alert_warning)
        GitHubAlertType.CAUTION -> stringResource(R.string.alert_caution)
    }

/**
 * GitHub Alert 检测 + GitHub 网页样式卡片（左侧色条 + 整卡淡底 + Octicons + 加粗标题）；
 * 非 Alert 复用官方 [MarkdownBlockQuote]。
 */
@Composable
fun GitHubAlertOrQuote(model: MarkdownComponentModel) {
    val nodeText = model.node.getUnescapedTextInNode(model.content)
    val parsed = GitHubAlertParser.parse(nodeText)
    if (parsed != null) {
        AlertCard(
            spec = alertSpec(alertLabel(parsed.type), parsed),
            body = parsed.body,
        )
    } else {
        StyledBlockQuote(nodeText)
    }
}

/**
 * 普通引用块：WebView github-markdown-css 同款观感——左侧主题色竖条 + 淡底 + 圆角。
 * 逐行渲染以保留「> 第一行 / > 第二行」的换行（mikepenz 默认会合并为一段）。
 */
@Composable
private fun StyledBlockQuote(text: String) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            text
                .lineSequence()
                .map { it.trimStart().removePrefix(">").removePrefix(" ") }
                .filter { it.isNotBlank() }
                .forEach { line ->
                    Markdown(content = line, modifier = Modifier.fillMaxWidth())
                }
        }
    }
}

@Composable
private fun AlertCard(
    spec: AlertSpec,
    body: String,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(spec.container),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(spec.accent),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = spec.label,
                    color = spec.onContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // 递归解析告警正文（保留 **加粗**、`行内码` 等行内格式）
            Markdown(
                content = body,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
