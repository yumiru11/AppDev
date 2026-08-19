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
    private val ALIGN_REGEX = Regex("""align\s*=\s*["']?center["']?""", RegexOption.IGNORE_CASE)

    fun parseAll(
        content: String,
        node: ASTNode,
        baseRepoUrl: String? = null,
    ): List<HtmlBadgeData> {
        if (node.type != MarkdownElementTypes.HTML_BLOCK) return emptyList()
        val nodeText = content.substring(node.startOffset, node.endOffset)
        val imgs = IMG_TAG_REGEX.findAll(nodeText).map { it.groupValues[1] }.toList()
        if (imgs.isEmpty()) return emptyList()
        val alignCenter = ALIGN_REGEX.containsMatchIn(nodeText)
        return imgs.mapNotNull { src ->
            val resolved =
                when {
                    src.startsWith("http") -> src
                    baseRepoUrl != null -> resolveRawUrl(baseRepoUrl, src)
                    else -> null
                }
            resolved?.let { HtmlBadgeData(imageSrc = it, linkHref = null, alignCenter = alignCenter) }
        }
    }

    private fun resolveRawUrl(
        baseRepoUrl: String,
        path: String,
    ): String = resolveRawImageUrl(baseRepoUrl, path)
}

/** 相对路径图片解析为 raw 域完整 URL（HTML 徽章 + Markdown 语法图共用）。 */
internal fun resolveRawImageUrl(
    baseRepoUrl: String,
    path: String,
): String {
    val stripped = baseRepoUrl.removePrefix("https://github.com/").trim('/')
    val cleanPath = path.trimStart('/')
    return "https://raw.githubusercontent.com/$stripped/HEAD/$cleanPath"
}
