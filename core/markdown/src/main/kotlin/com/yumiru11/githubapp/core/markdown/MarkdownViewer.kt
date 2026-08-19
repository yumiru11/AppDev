@file:Suppress("LongMethod") // MarkdownViewer 视觉装配集中（typography/colors/components），拆分反损可读性（EnhancedMarkdownViewer 同款先例）

package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.RoundedCornerSpanPainter
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.rememberMarkdownState
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * Markdown 渲染组件（原生短文本专用：Issue 评论/通知）。
 *
 * 基于 mikepenz multiplatform-markdown-renderer 0.38.1 + KotlinTextMate 高亮。
 * 视觉策略与 [EnhancedMarkdownViewer] 对齐（WebView github-markdown-css 观感）：
 * 行内代码主题色+圆角、代码块卡片背景、列表缩进、任务列表 checkbox、主题色引用块左条。
 *
 * @param markdown Markdown 原文
 * @param onInternalLink 链接点击回调；Internal 类型（Repo/Issue/PR 等）由上层路由处理，
 *   External 类型默认打开 CustomTabs（上层处理）；默认空实现
 * @param modifier Modifier
 * @param scrollable 是否自带纵向滚动。true（默认）：组件内部应用 verticalScroll；
 *   false：内容全展开由外层滚动容器承担（IssueDetailScreen 等 LazyColumn item 内
 *   使用——Lazy 列表 item 测量约束为无限高，内嵌 verticalScroll 会崩
 *   「Vertically scrollable component was measured with an infinity maximum height
 *   constraints」，RoadWeaver 崩溃根因 2026-08-17）
 */
@Composable
fun MarkdownViewer(
    markdown: String,
    onInternalLink: (ParsedUrl) -> Unit = {},
    baseRepoUrl: String? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val darkTheme = isSystemInDarkTheme()
    val state = rememberMarkdownState(markdown, immediate = true)
    val scheme = MaterialTheme.colorScheme

    // 链接点击接线：renderer 0.38.1 无 link 槽位，所有链接统一走 LocalUriHandler
    // （annotatorSettings 内部唯一消费点，构建 LinkAnnotation.Url 后经 openUri 分发）。
    // 在此覆盖 handler，把每次点击解析为 ParsedUrl 后交上层（Internal→应用内导航，
    // External→CustomTabs）。上层可能重组替换回调，用 rememberUpdatedState 取最新值。
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

    val colors =
        markdownColor(
            text = scheme.onSurface,
            codeBackground = if (darkTheme) scheme.surfaceContainerLowest else scheme.surfaceContainer,
            inlineCodeBackground = scheme.primary.copy(alpha = 0.10f),
            dividerColor = scheme.outlineVariant,
            tableBackground = scheme.surfaceContainerLow,
        )

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

    CompositionLocalProvider(LocalUriHandler provides linkUriHandler) {
        Markdown(
            state,
            imageTransformer = Coil3ImageTransformerImpl,
            colors = colors,
            typography = typography,
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
                                topMargin = 8.sp,
                                bottomMargin = 4.sp,
                            ),
                        )
                    }
                },
            components =
                markdownComponents(
                    codeFence = { model ->
                        MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, _ ->
                            EnhancedCodeBlock(code, language, darkTheme)
                        }
                    },
                    blockQuote = { model -> GitHubAlertOrQuote(model) },
                    unorderedList = { model -> EnhancedUnorderedList(model) },
                    orderedList = { model -> EnhancedOrderedList(model) },
                    checkbox = { model -> MarkdownCheckBox(model.content, model.node, model.typography.text) },
                ),
            modifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier,
        )
    }
}

/**
 * 解析 Markdown 链接 URL 为 [ParsedUrl]。
 *
 * 内部链接（GitHub 域名）返回具体的 [ParsedUrl] 子类型（Repo/Issue/PR 等），
 * 外部链接返回 [ParsedUrl.External]。
 *
 * 用法示例：
 * ```kotlin
 * val parsed = parseMarkdownLink("https://github.com/owner/repo/issues/42")
 * // parsed = ParsedUrl.Issue("owner", "repo", 42)
 * ```
 */
fun parseMarkdownLink(
    url: String,
    baseRepoUrl: String? = null,
): ParsedUrl = GitHubLinkParser.parseUrl(resolveMarkdownUrl(url, baseRepoUrl))

/**
 * 仓库上下文相对链接解析（2026-08-14 真机走查修复：README 相对链接跳浏览器）。
 *
 * - 绝对 URL（http/https/mailto）→ 原样返回
 * - 相对路径（`docs/x.md`、`./x`、`/x`）→ `{baseRepoUrl}/blob/HEAD/{path}`
 * - 锚点（`#section`）→ `{baseRepoUrl}#section`
 * - 无 [baseRepoUrl] → 原样返回（无上下文可解析）
 */
fun resolveMarkdownUrl(
    url: String,
    baseRepoUrl: String?,
): String {
    if (baseRepoUrl == null) return url
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) return url

    val trimmed = url.removePrefix("./").removePrefix("/")
    return if (trimmed.startsWith("#")) {
        "$baseRepoUrl$trimmed"
    } else {
        "$baseRepoUrl/blob/HEAD/$trimmed"
    }
}

/**
 * Markdown 链接点击分流：解析 URL 后回调 [onInternalLink]。
 *
 * 纯函数（无 Compose/Android 依赖，可 JVM 单测）。内部链接回调具体 [ParsedUrl]
 * 子类型（Repo/Issue/PR/User 等，上层做应用内导航），外部链接回调
 * [ParsedUrl.External]（上层经 ExternalLinkHost 开 CustomTabs）。
 */
fun dispatchMarkdownLink(
    url: String,
    baseRepoUrl: String? = null,
    onInternalLink: (ParsedUrl) -> Unit,
) {
    onInternalLink(parseMarkdownLink(url, baseRepoUrl))
}
