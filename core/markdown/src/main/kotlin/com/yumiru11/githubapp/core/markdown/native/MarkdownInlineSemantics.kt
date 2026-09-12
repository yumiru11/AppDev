package com.yumiru11.githubapp.core.markdown.native

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * 原生链内联语义补齐（mikepenz 0.38.1 的 `MarkdownAnnotator` 钩子，2026-09-12）。
 *
 * 默认注解器不认 `HTML_TAG` 节点：`<kbd>` / `<sub>` / `<sup>` 只剩中间文字（纯文本），
 * 且正文里的 `@user` 完全不渲染。本注解器补两件事：
 *
 * 1. **`@user` 提及 → 主题色链接**（`https://github.com/<user>`）：点击由 `LocalUriHandler`
 *    交给 `GitHubLinkParser` → `ParsedUrl.User` → 应用内用户页；
 * 2. **`<kbd>` / `<sub>` / `<sup>` 语义**：kbd = 等宽 + 底色键帽，sub/sup = `BaselineShift`
 *    上下标 + 缩小字号（对齐 GitHub 网页端排版语义）。
 *
 * 结构保证（与离线通道 `renderer.js` 的 `mentionPlugin` 同一套规则，两条通道行为一致）：
 * - 只处理 `TEXT` 与 kbd/sub/sup 的 `HTML_TAG` 节点，其余返回 false 走 mikepenz 默认处理；
 * - 代码（`CODE_SPAN` / `CODE_FENCE` / `CODE_BLOCK`）、已有链接与自动链接、邮箱、图片、
 *   `HTML_BLOCK` 内部一律跳过 → **代码里的 `@user` 绝不被链接化**（已知陷阱）。
 *
 * 提及词法只覆盖**用户**：`@org/team` 无应用内路由（`GitHubLinkParser` 明确归为 External），
 * 整段保持纯文本，绝不做半截 `@org` 链接（负向前瞻挡住 `/`）。
 */
object MarkdownInlineSemantics {
    private const val PROFILE_BASE = "https://github.com/"

    /** GitHub 用户名为 1–39 位字母/数字/连字符，首尾不得是连字符（与网页端一致）。 */
    private val MENTION =
        Regex(
            """(^|[^\w./+@-])(@([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?))(?![A-Za-z0-9\-/])""",
        )

    private val OPEN_TAG = Regex("""^<(kbd|sub|sup)(?:\s[^>]*)?>$""", RegexOption.IGNORE_CASE)
    private val CLOSE_TAG = Regex("""^</(kbd|sub|sup)\s*>$""", RegexOption.IGNORE_CASE)

    /**
     * 这些祖先之下的 TEXT 不参与任何内联改写：代码块/行内代码里出现的 `@user`、
     * `<kbd>` 示例必须保持原文；已有链接/自动链接/邮箱/图片里不能注入嵌套链接。
     */
    private val SKIPPED_ANCESTORS: Set<IElementType> =
        setOf(
            MarkdownElementTypes.CODE_SPAN,
            MarkdownElementTypes.CODE_FENCE,
            MarkdownElementTypes.CODE_BLOCK,
            MarkdownElementTypes.HTML_BLOCK,
            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK,
            MarkdownElementTypes.AUTOLINK,
            MarkdownElementTypes.LINK_DESTINATION,
            MarkdownElementTypes.LINK_LABEL,
            MarkdownElementTypes.IMAGE,
            GFMTokenTypes.GFM_AUTOLINK,
            MarkdownTokenTypes.EMAIL_AUTOLINK,
        )

    /** 构造原生链注解器（mikepenz 公开工厂：annotate 带 Builder 接收者，返回 true = 消费该节点）。 */
    fun annotator(styles: MarkdownInlineStyles): MarkdownAnnotator =
        markdownAnnotator { content, node -> annotateNode(this, content, node, styles) }

    /** 单个 TEXT/HTML_TAG 节点的注解入口；true = 本节点已处理（跳过默认逻辑）。 */
    private fun annotateNode(
        builder: AnnotatedString.Builder,
        content: String,
        node: ASTNode,
        styles: MarkdownInlineStyles,
    ): Boolean =
        when {
            isSemanticTag(node, content) -> true
            node.type == MarkdownTokenTypes.TEXT -> annotateText(builder, content, node, styles)
            else -> false
        }

    private fun annotateText(
        builder: AnnotatedString.Builder,
        content: String,
        node: ASTNode,
        styles: MarkdownInlineStyles,
    ): Boolean {
        if (hasSkippedAncestor(node)) return false
        val raw = content.substring(node.startOffset, node.endOffset)

        // kbd/sub/sup 包裹的文本：整段套 SpanStyle（HTML 的自动闭合语义——未闭合时直到块尾）
        val tag = enclosingSemanticTag(node, content)
        if (tag != null) {
            builder.pushStyle(styles.forTag(tag))
            builder.append(raw)
            builder.pop()
            return true
        }
        return appendMentions(builder, raw, styles.link)
    }

    /** TEXT 是否在 kbd/sub/sup 开标签与对应闭标签之间（就近配对，考虑同类嵌套）。 */
    private fun enclosingSemanticTag(
        node: ASTNode,
        content: String,
    ): String? {
        val siblings = node.parent?.children ?: return null
        val closed = mutableMapOf<String, Int>()
        var found: String? = null
        var index = siblings.indexOfFirst { it === node } - 1
        while (index >= 0 && found == null) {
            found = advanceTagScan(siblings[index], content, closed)
            index--
        }
        return found
    }

    /**
     * 扫描单个兄弟节点（从右向左）：闭标签登记、已配对的开标签消账；
     * 未配对的开标签 = 当前 TEXT 所处的语义包裹（返回其名），其它情况返回 null。
     */
    private fun advanceTagScan(
        sibling: ASTNode,
        content: String,
        closed: MutableMap<String, Int>,
    ): String? {
        if (sibling.type != MarkdownTokenTypes.HTML_TAG) return null
        val text = content.substring(sibling.startOffset, sibling.endOffset).trim()
        val close = tagName(CLOSE_TAG, text)
        val open = tagName(OPEN_TAG, text)
        val outstanding = if (open != null) closed[open] ?: 0 else 0
        return when {
            close != null -> {
                closed[close] = (closed[close] ?: 0) + 1
                null
            }

            open == null -> {
                null
            }

            outstanding > 0 -> {
                closed[open] = outstanding - 1
                null
            }

            else -> {
                open
            }
        }
    }

    /** kbd/sub/sup 的开/闭标签本身：消费掉（默认逻辑本就不输出其它 HTML 标签，语义由内容 Span 承担）。 */
    private fun isSemanticTag(
        node: ASTNode,
        content: String,
    ): Boolean {
        if (node.type != MarkdownTokenTypes.HTML_TAG) return false
        if (hasSkippedAncestor(node)) return false
        val text = content.substring(node.startOffset, node.endOffset).trim()
        return tagName(OPEN_TAG, text) != null || tagName(CLOSE_TAG, text) != null
    }

    private fun tagName(
        pattern: Regex,
        text: String,
    ): String? =
        pattern
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.lowercase()

    private fun hasSkippedAncestor(node: ASTNode): Boolean {
        var parent = node.parent
        while (parent != null) {
            if (parent.type in SKIPPED_ANCESTORS) return true
            parent = parent.parent
        }
        return false
    }

    /**
     * 在 text 内容里把提及写成 `pushLink` 段；无提及返回 false（走默认处理）。
     *
     * 前导字符（空白/标点）并入前一段纯文本，不能吞掉。
     */
    private fun appendMentions(
        builder: AnnotatedString.Builder,
        raw: String,
        linkStyles: TextLinkStyles,
    ): Boolean {
        val matches = MENTION.findAll(raw).toList()
        if (matches.isEmpty()) return false

        var cursor = 0
        for (match in matches) {
            val prefix = match.groupValues[1]
            val mention = match.groupValues[2]
            val user = match.groupValues[3]
            val before = raw.substring(cursor, match.range.first) + prefix
            if (before.isNotEmpty()) builder.append(before)
            builder.pushLink(LinkAnnotation.Url(PROFILE_BASE + user, linkStyles))
            builder.append(mention)
            builder.pop()
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) builder.append(raw.substring(cursor))
        return true
    }
}

/** 原生链内联语义的主题化样式（由 [rememberMarkdownInlineSemantics] 从 Material You 解析）。 */
data class MarkdownInlineStyles(
    val link: TextLinkStyles,
    val kbd: SpanStyle,
    val subscript: SpanStyle,
    val superscript: SpanStyle,
) {
    fun forTag(name: String): SpanStyle =
        when (name) {
            "kbd" -> kbd
            "sub" -> subscript
            else -> superscript
        }
}

/**
 * 与两条原生渲染器（[com.yumiru11.githubapp.core.markdown.MarkdownViewer] /
 * [com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer]）同一套主题取值。
 *
 * 用 `Markdown(annotator = …)` 传入后，mikepenz 会把注解读取器经 CompositionLocal 下发到
 * 所有 `MarkdownText`/标题/列表段落；点击链接仍走调用方提供的 `LocalUriHandler`。
 */
@Composable
fun rememberMarkdownInlineSemantics(): MarkdownAnnotator {
    val scheme = MaterialTheme.colorScheme
    val link =
        TextLinkStyles(
            style = SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline),
        )
    return remember(link, scheme.onSurface, scheme.surfaceContainerHigh) {
        MarkdownInlineSemantics.annotator(
            MarkdownInlineStyles(
                link = link,
                kbd =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = scheme.onSurface,
                        background = scheme.surfaceContainerHigh,
                    ),
                subscript = SpanStyle(fontSize = 12.sp, baselineShift = BaselineShift.Subscript),
                superscript = SpanStyle(fontSize = 12.sp, baselineShift = BaselineShift.Superscript),
            ),
        )
    }
}
