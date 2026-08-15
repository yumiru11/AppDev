@file:Suppress("LongMethod") // B 增强渲染器装配点集中（typography/colors/components），拆分反损可读性。

package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.RoundedCornerSpanPainter
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import org.intellij.markdown.MarkdownElementTypes

/**
 * B「原生增强版」Markdown 渲染组件（prototype/readme-comparison）。
 *
 * 基于 mikepenz 0.38.1 扩展，不重写解析链路：
 * - GitHub 排版基准：正文 16sp/1.6，标题 H1 32 / H2 24 / H3 20 / H4 16
 * - 增强表格、代码块（复制 + GitHub 原色高亮）、Alert、details、图片预览、分隔线
 * - 链接仍统一走 [dispatchMarkdownLink] → 上层应用内导航
 */
@Composable
fun EnhancedMarkdownViewer(
    markdown: String,
    onInternalLink: (ParsedUrl) -> Unit = {},
    baseRepoUrl: String? = null,
    modifier: Modifier = Modifier,
    imageTransformer: ImageTransformer = Coil3ImageTransformerImpl,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val state = rememberMarkdownState(markdown, immediate = true)
    val scheme = MaterialTheme.colorScheme
    val currentOnInternalLink by rememberUpdatedState(onInternalLink)
    val currentBaseRepoUrl by rememberUpdatedState(baseRepoUrl)
    val linkUriHandler =
        remember {
            object : UriHandler {
                override fun openUri(uri: String) {
                    dispatchMarkdownLink(uri, currentBaseRepoUrl, currentOnInternalLink)
                }
            }
        }

    val typography =
        markdownTypography(
            h1 = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
            h2 = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
            h3 = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
            h4 = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
            h5 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
            h6 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant),
            text = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp, color = scheme.onSurface),
            code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurface),
            inlineCode = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.primary),
            quote = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp, color = scheme.onSurface),
            paragraph = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp, color = scheme.onSurface),
            ordered = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp, color = scheme.onSurface),
            bullet = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp, color = scheme.onSurface),
            list = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp, color = scheme.onSurface),
            textLink =
                TextLinkStyles(
                    style =
                        androidx.compose.ui.text
                            .SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline),
                ),
            table = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = scheme.onSurface),
        )

    val colors =
        markdownColor(
            text = scheme.onSurface,
            codeBackground = if (darkTheme) scheme.surfaceContainerLowest else scheme.surfaceContainer,
            inlineCodeBackground = scheme.primary.copy(alpha = 0.1f),
            dividerColor = scheme.outlineVariant,
            tableBackground = scheme.surfaceContainerLow,
        )

    androidx.compose.foundation.layout.Box(modifier = modifier.padding(horizontal = 37.dp)) {
        CompositionLocalProvider(LocalUriHandler provides linkUriHandler) {
            Markdown(
                state,
                padding =
                    markdownPadding(
                        block = 16.dp,
                        listItemTop = 8.dp,
                        listItemBottom = 8.dp,
                        listIndent = 20.dp,
                    ),
                extendedSpans =
                    markdownExtendedSpans {
                        remember {
                            ExtendedSpans(
                                RoundedCornerSpanPainter(
                                    cornerRadius = 4.sp,
                                    padding =
                                        com.mikepenz.markdown.compose.extendedspans
                                            .RoundedCornerSpanPainter
                                            .TextPaddingValues(horizontal = 4.sp, vertical = 2.sp),
                                    // 官方默认 topMargin/bottomMargin=1sp 是占位值：框画在文本
                                    // 顶部偏上。按 (行高-字号)/2 计算让框垂直居中（2026-08-16 真机验证）。
                                    topMargin = 8.sp,
                                    bottomMargin = 4.sp,
                                ),
                            )
                        }
                    },
                colors = colors,
                typography = typography,
                imageTransformer = imageTransformer,
                components =
                    markdownComponents(
                        blockQuote = { model -> GitHubAlertOrQuote(model) },
                        codeFence = { model ->
                            MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, _ ->
                                EnhancedCodeBlock(
                                    code = code,
                                    language = language,
                                    isDark = darkTheme,
                                )
                            }
                        },
                        horizontalRule = { EnhancedHorizontalRule() },
                        heading1 = { model ->
                            MarkdownHeadingWithDivider(
                                model,
                                fontSize = 32.sp,
                                lineHeight = 40.sp,
                                dividerColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        },
                        heading2 = { model ->
                            MarkdownHeadingWithDivider(
                                model,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                dividerColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        },
                        unorderedList = { model -> EnhancedUnorderedList(model) },
                        orderedList = { model -> EnhancedOrderedList(model) },
                        checkbox = { model -> MarkdownCheckBox(model.content, model.node, model.typography.text) },
                        image = { model -> EnhancedMarkdownImage(model) },
                        table = { model ->
                            EnhancedMarkdownTable(model = model)
                        },
                        custom = { type, model ->
                            if (type == MarkdownElementTypes.HTML_BLOCK) {
                                EnhancedHtmlBlock(model)
                            } else {
                                MarkdownText(content = model.content, node = model.node, style = model.typography.text)
                            }
                        },
                    ),
                modifier = modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * 标题 + 底部分割线（对齐 WebView 的 github-markdown-css h1/h2 border-bottom 观感）。
 */
@Composable
private fun MarkdownHeadingWithDivider(
    model: com.mikepenz.markdown.compose.components.MarkdownComponentModel,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    dividerColor: androidx.compose.ui.graphics.Color,
) {
    androidx.compose.foundation.layout.Column {
        com.mikepenz.markdown.compose.elements.MarkdownHeader(
            content = model.content,
            node = model.node,
            style =
                model.typography.text.copy(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                ),
        )
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = dividerColor,
            modifier =
                androidx.compose.ui.Modifier
                    .padding(top = 8.dp, bottom = 16.dp),
        )
    }
}
