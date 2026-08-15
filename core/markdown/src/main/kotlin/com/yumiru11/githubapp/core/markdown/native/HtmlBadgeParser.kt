package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

/** HTML 徽章块解析结果（`<p align="center"><img src="..."></p>` 等）。 */
data class HtmlBadgeData(
    val imageSrc: String,
    val linkHref: String?,
    val alignCenter: Boolean,
)

/**
 * HTML_BLOCK 里的 shields 徽章解析器（纯函数，spike）。
 *
 * GitHub README 大量使用 HTML 形态徽章（`<p align="center"><img src="badge.svg"></p>`
 * 或 `<div align="center">` 包一组），GFM 解析为 HTML_BLOCK——原生 EnhancedHtmlBlock
 * 只认识 `<details>`，其余渲染空（MarkdownText 对 HTML_BLOCK 不提取文本，2026-08-16 探针验证）。
 * 本类提取 img src（+ 可选 a 包裹链接 + align），无法解析返回 null。
 */
object HtmlBadgeParser {
    private val IMG_TAG_REGEX = Regex("""<img[^>]*\ssrc="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val ANCHOR_TAG_REGEX = Regex("""<a\s+[^>]*href="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    private val ALIGN_REGEX = Regex("""align\s*=\s*["']?center["']?""", RegexOption.IGNORE_CASE)

    fun parse(
        content: String,
        node: ASTNode,
    ): HtmlBadgeData? {
        if (node.type != MarkdownElementTypes.HTML_BLOCK) return null
        val nodeText = content.substring(node.startOffset, node.endOffset)
        // 多个 <img>（徽章组）时只处理第一个，其余留给后续（原型验证单徽章场景）
        val img = IMG_TAG_REGEX.find(nodeText) ?: return null
        val src = img.groupValues[1]
        if (!src.startsWith("http")) return null
        val anchor = ANCHOR_TAG_REGEX.find(nodeText)
        return HtmlBadgeData(
            imageSrc = src,
            linkHref = anchor?.groupValues?.get(1),
            alignCenter = ALIGN_REGEX.containsMatchIn(nodeText),
        )
    }
}
