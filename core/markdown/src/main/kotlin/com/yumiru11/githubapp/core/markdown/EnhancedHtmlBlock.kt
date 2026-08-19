package com.yumiru11.githubapp.core.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.m3.Markdown
import com.yumiru11.githubapp.core.markdown.native.HtmlBadgeParser
import com.yumiru11.githubapp.core.markdown.native.HtmlDetailsParser

/**
 * HTML_BLOCK 原生处理：识别 `<details>` 时自研折叠；徽章组（含相对路径 img）渲染为
 * 20dp 徽章 Row；其余 HTML 降级为去标签纯文本（不展示源码）。
 *
 * 这是 mikepenz 0.38.1 无 details 槽的 spike；若 future 版本提供 details 槽应替换本组件。
 */
@Composable
fun EnhancedHtmlBlock(
    model: MarkdownComponentModel,
    baseRepoUrl: String? = null,
) {
    val details = remember(model.content, model.node) { HtmlDetailsParser.parse(model.content, model.node) }
    val badges =
        remember(model.content, model.node, baseRepoUrl) {
            HtmlBadgeParser.parseAll(model.content, model.node, baseRepoUrl)
        }
    when {
        details != null -> {
            NativeDetailsCard(summary = details.summary, body = details.body)
        }

        badges.isNotEmpty() -> {
            NativeBadgeRow(badges = badges)
        }

        !isClosingDetailsOnly(model) -> {
            // HTML_BLOCK 无 details/徽章时：不展示源码（用户可见「未渲染 HTML 标签」），
            // 降级为去标签纯文本——保留内容可读性（2026-08-17 真机：EchoMusic/mikepenz
            // HTML 段直接展示源码，要求修复）。
            val htmlText = model.content.substring(model.node.startOffset, model.node.endOffset)
            Text(
                text = stripHtmlTags(htmlText),
                style = model.typography.text.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/** 去除 HTML 标签并解码常见实体（保留换行），HTML 块降级文本化用。 */
internal fun stripHtmlTags(html: String): String =
    html
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

private fun isClosingDetailsOnly(model: MarkdownComponentModel): Boolean {
    val text = model.content.substring(model.node.startOffset, model.node.endOffset).trim()
    return text.equals("</details>", ignoreCase = true)
}

@Composable
private fun NativeDetailsCard(
    summary: String,
    body: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "detailsArrow")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Chevron_right,
                    contentDescription = stringResource(R.string.details_expand),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).rotate(arrowRotation),
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Markdown(
                    content = body,
                    modifier = Modifier.padding(start = 38.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/**
 * HTML 徽章组渲染：多个 20dp 徽章 Row 平铺（可居中）。相对路径 img 已在
 * [HtmlBadgeParser.parseAll] 解析为 raw 域完整 URL。
 */
@Composable
private fun NativeBadgeRow(badges: List<com.yumiru11.githubapp.core.markdown.native.HtmlBadgeData>) {
    val shape = MaterialTheme.shapes.medium
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            badges.forEach { badge ->
                Box(
                    modifier =
                        Modifier
                            .shadow(8.dp, shape)
                            .clip(shape),
                ) {
                    coil3.compose.AsyncImage(
                        model = badge.imageSrc,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.height(20.dp),
                    )
                }
            }
        }
    }
    if (badges.first().alignCenter) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { rowContent() }
    } else {
        rowContent()
    }
}
