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
    return "https://raw.githubusercontent.com/$stripped/HEAD/${normalizeRelativePath(path)}"
}

/**
 * 相对路径归一化：去掉 `./`、折叠 `../`（越过仓库根时钳制在根，与 #232 离线通道同语义）。
 *
 * GitHub 正文里的相对图片几乎都写成 `./docs/x.png`；原样拼进 raw URL 会得到
 * `…/HEAD/./docs/x.png`（部分 CDN 路径不规范会 404），必须先归一化（缺陷 #2 的配套）。
 */
private fun normalizeRelativePath(path: String): String {
    val stack = ArrayDeque<String>()
    path.trimStart('/').split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (stack.isNotEmpty()) stack.removeLast()
            else -> stack.addLast(segment)
        }
    }
    return stack.joinToString("/")
}
