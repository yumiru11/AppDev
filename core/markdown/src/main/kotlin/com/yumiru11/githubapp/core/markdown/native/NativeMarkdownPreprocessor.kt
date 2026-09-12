package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.util.Base64

/**
 * 原生链渲染前的纯文本预处理（补齐 intellij-markdown / mikepenz 0.38.1 不提供的两件事）。
 *
 * ## 1. `<script>` 元素整体删除（缺陷 #4）
 *
 * 行内 `<script>alert(1)</script>` 在 AST 里是 `HTML_TAG("("` + 若干 `TEXT` + `HTML_TAG(")")`：
 * 标签本身不渲染，但标签之间的正文（`alert(1)`）会被当普通文本拼进段落。仅删标签不够，
 * 必须把元素正文一并丢弃。块级脚本（整行起 `<script>`）是 `HTML_BLOCK`，由
 * [com.yumiru11.githubapp.core.markdown.stripHtmlTags] 兜底。
 *
 * 删除锚点取自解析树中的 `HTML_TAG` 节点——**代码围栏/行内代码里出现的 `<script>` 不会
 * 产生 `HTML_TAG` 节点**，因此文档示例不受误伤（这是不走裸正则的原因）。
 *
 * ## 2. `<details>` 区域折叠为单块（缺陷 #1）
 *
 * GitHub 惯用的空行写法：
 * ```
 * <details>
 * <summary>X</summary>
 *
 * body（Markdown）
 *
 * </details>
 * ```
 * GFM 会拆成三个块：开标签 `HTML_BLOCK` / 正文段落 / 闭标签 `HTML_BLOCK`。旧实现只在
 * 开标签块向 `</details>` 借用区间渲染卡片正文，正文段落仍被外层解析照常渲染 → **同一段
 * 文字出现两次**（折叠时也常驻可见）。
 *
 * 修复：把整个 `<details>…</details>` 区域替换成一个**无空行的合成 HTML 块**，正文与
 * summary 以 base64 载荷存放（base64 不含换行、不含 `<`/`>`，不会被 Markdown 二次解析）。
 * 合成块在 AST 里是**唯一一个** `HTML_BLOCK`，由 [HtmlDetailsParser] 解码后交给
 * `NativeDetailsCard` 重新解析 body——外层不再有任何正文节点，折叠语义恢复。
 *
 * 该替换同样只针对解析树里的 `HTML_BLOCK` 节点，代码围栏中的 `<details>` 示例保持原样。
 */
object NativeMarkdownPreprocessor {
    /** 合成 details 块里的 summary 载荷标签（解析回读见 [HtmlDetailsParser]）。 */
    internal const val SUMMARY_TAG: String = "mdsummary"

    /** 合成 details 块里的 body 载荷标签。 */
    internal const val BODY_TAG: String = "mdbody"

    private const val CLOSE_DETAILS: String = "</details>"
    private val OPEN_DETAILS = Regex("""(?is)<details\b[^>]*>""")
    private val SUMMARY = Regex("""(?is)<summary\b[^>]*>(.*?)</summary>""")
    private val OPEN_SCRIPT = Regex("""(?is)<script\b[^>]*>""")
    private val CLOSE_SCRIPT = Regex("""(?is)</script\s*>""")
    private val SCRIPT_ELEMENT = Regex("""(?is)<script\b[^>]*>.*?</script\s*>""")

    /**
     * 渲染前预处理：先删 `<script>` 元素，再把 `<details>` 区域折叠为单块。
     *
     * 顺序不可颠倒——若先折叠 details，脚本正文会以 base64 进入卡片，展开后仍会显示。
     */
    fun prepare(markdown: String): String {
        if (markdown.isEmpty()) return markdown
        return collapseDetails(removeScriptElements(markdown))
    }

    /** base64 载荷编码（`NativeDetailsCard` 渲染的 markdown 内容不受编码影响）。 */
    internal fun encodeDetailPayload(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    /** base64 载荷解码；非法载荷返回 null（调用方降级为普通文本）。 */
    internal fun decodeDetailPayload(value: String): String? =
        runCatching { Base64.getDecoder().decode(value.trim()).toString(Charsets.UTF_8) }.getOrNull()

    private fun removeScriptElements(markdown: String): String {
        val ranges =
            (inlineScriptRanges(markdown) + blockScriptRanges(markdown))
                .sortedBy { it.first }
        if (ranges.isEmpty()) return markdown
        return replaceRanges(markdown, ranges)
    }

    /** 段落内的行内脚本：`HTML_TAG("<script…>")` 到匹配的 `HTML_TAG("</script>")`。 */
    private fun inlineScriptRanges(markdown: String): List<IntRange> {
        val tags = tagsOf(markdown, MarkdownTokenTypes.HTML_TAG)
        if (tags.isEmpty()) return emptyList()
        return scriptElementRanges(markdown, tags)
    }

    /** 独占一行的脚本是整块 `HTML_BLOCK`（GFM type 1，止于 `</script>`）。 */
    private fun blockScriptRanges(markdown: String): List<IntRange> =
        tagsOf(markdown, MarkdownElementTypes.HTML_BLOCK)
            .filter { SCRIPT_ELEMENT.matches(textOf(markdown, it).trim()) }
            .map { it.startOffset until it.endOffset }

    /** 成对 `<script>…</script>` 的整段删除区间（未闭合的开标签不处理，避免误删大段正文）。 */
    private fun scriptElementRanges(
        markdown: String,
        tags: List<ASTNode>,
    ): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < tags.size) {
            val closeIndex =
                if (OPEN_SCRIPT.matches(textOf(markdown, tags[index]))) {
                    findClosingScriptTag(markdown, tags, index + 1)
                } else {
                    -1
                }
            if (closeIndex < 0) {
                index++
            } else {
                ranges += tags[index].startOffset until tags[closeIndex].endOffset
                index = closeIndex + 1
            }
        }
        return ranges
    }

    private fun findClosingScriptTag(
        markdown: String,
        tags: List<ASTNode>,
        fromIndex: Int,
    ): Int {
        for (index in fromIndex until tags.size) {
            if (CLOSE_SCRIPT.matches(textOf(markdown, tags[index]))) return index
        }
        return -1
    }

    private fun collapseDetails(markdown: String): String {
        val blocks = tagsOf(markdown, MarkdownElementTypes.HTML_BLOCK)
        if (blocks.isEmpty()) return markdown
        val builder = StringBuilder(markdown.length)
        var cursor = 0
        var replaced = false
        blocks.forEach { node ->
            if (node.startOffset < cursor) return@forEach
            if (!OPEN_DETAILS.containsMatchIn(textOf(markdown, node))) return@forEach
            val close = markdown.indexOf(CLOSE_DETAILS, node.endOffset, ignoreCase = true)
            if (close < 0) return@forEach
            builder.append(markdown, cursor, node.startOffset)
            builder.append(encodeDetailsBlock(markdown.substring(node.startOffset, close), close - node.startOffset))
            cursor = close + CLOSE_DETAILS.length
            replaced = true
        }
        if (!replaced) return markdown
        builder.append(markdown, cursor, markdown.length)
        return builder.toString()
    }

    /** 生成**无空行**的合成块：GFM 的 HTML block 只在空行处结束，无空行才能保证单块。 */
    private fun encodeDetailsBlock(
        rawRegion: String,
        closeIndex: Int,
    ): String {
        val summaryMatch = SUMMARY.find(rawRegion)
        val summary =
            summaryMatch
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()
        val bodyStart =
            summaryMatch?.range?.last?.plus(1)
                ?: OPEN_DETAILS
                    .find(rawRegion)
                    ?.range
                    ?.last
                    ?.plus(1)
                ?: 0
        val body = rawRegion.substring(bodyStart.coerceAtMost(closeIndex), closeIndex).trim()
        return buildString {
            append("<details>\n")
            appendPayload(SUMMARY_TAG, summary)
            append('\n')
            appendPayload(BODY_TAG, body)
            append("\n</details>")
        }
    }

    private fun StringBuilder.appendPayload(
        tag: String,
        value: String,
    ) {
        append('<').append(tag).append('>')
        append(encodeDetailPayload(value))
        append("</").append(tag).append('>')
    }

    private fun replaceRanges(
        markdown: String,
        ranges: List<IntRange>,
    ): String {
        val builder = StringBuilder(markdown.length)
        var cursor = 0
        ranges.forEach { range ->
            if (range.first < cursor) return@forEach
            builder.append(markdown, cursor, range.first)
            cursor = range.last + 1
        }
        builder.append(markdown, cursor, markdown.length)
        return builder.toString()
    }

    /** 收集指定类型的 AST 节点（按源文偏移排序；解析器给出的顺序已是文档序，排序兜底）。 */
    private fun tagsOf(
        markdown: String,
        type: org.intellij.markdown.IElementType,
    ): List<ASTNode> {
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
        val nodes = mutableListOf<ASTNode>()
        collectNodes(root, type, nodes)
        return nodes.sortedBy { it.startOffset }
    }

    private fun collectNodes(
        node: ASTNode,
        type: org.intellij.markdown.IElementType,
        out: MutableList<ASTNode>,
    ) {
        if (node.type == type) out += node
        node.children.forEach { collectNodes(it, type, out) }
    }

    private fun textOf(
        markdown: String,
        node: ASTNode,
    ): String = markdown.substring(node.startOffset, node.endOffset)
}
