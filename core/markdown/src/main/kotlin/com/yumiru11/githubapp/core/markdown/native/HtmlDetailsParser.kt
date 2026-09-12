package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

/** `<details>` HTML block 解析结果。 */
data class HtmlDetailsData(
    val summary: String,
    val body: String,
)

/**
 * `<details>` 原生折叠解析器（纯函数）。
 *
 * mikepenz 0.38.1 没有 details 槽；HTML_BLOCK 经 `custom` 槽分发后用本类提取
 * summary 与正文。无法解析时返回 null，由调用方降级为原始文本。
 *
 * 生产路径的 content 已由 [NativeMarkdownPreprocessor.prepare] 折叠为单个合成块
 * （summary/body 以 base64 载荷存放，见 [NativeMarkdownPreprocessor]），因此
 * 优先解析合成形态；保留的原始 HTML 形态解析供未经预处理的直接调用兜底。
 */
object HtmlDetailsParser {
    private val SYNTHETIC_SUMMARY = Regex("""(?is)<mdsummary>(.*?)</mdsummary>""")
    private val SYNTHETIC_BODY = Regex("""(?is)<mdbody>(.*?)</mdbody>""")
    private val DETAILS_REGEX =
        Regex(
            """(?is)<details\s*>\s*<summary>(.*?)</summary>\s*(.*?)</details>""",
        )

    fun parse(
        content: String,
        node: ASTNode,
    ): HtmlDetailsData? {
        if (node.type != MarkdownElementTypes.HTML_BLOCK) return null
        val nodeText = content.substring(node.startOffset, node.endOffset)
        return parseSynthetic(nodeText) ?: parseRawDetails(content, node, nodeText)
    }

    private fun parseRawDetails(
        content: String,
        node: ASTNode,
        nodeText: String,
    ): HtmlDetailsData? {
        val raw = borrowToCloseTag(content, node, nodeText) ?: return null
        val match = DETAILS_REGEX.find(raw) ?: return null
        return HtmlDetailsData(
            summary = match.groupValues[1].trim(),
            body = match.groupValues[2].trim(),
        )
    }

    /**
     * GFM 会把 `<details>`/`<summary>` 与 `</details>` 拆成多个 HTML_BLOCK；
     * 只有首块包含 `<details` 时，才向后续内容借用到 `</details>` 的完整区间。
     */
    private fun borrowToCloseTag(
        content: String,
        node: ASTNode,
        nodeText: String,
    ): String? {
        val splitAcrossBlocks =
            nodeText.contains("<details", ignoreCase = true) &&
                !nodeText.contains("</details>", ignoreCase = true)
        if (!splitAcrossBlocks) return nodeText
        val closeStart = content.indexOf("</details>", node.endOffset, ignoreCase = true)
        if (closeStart < 0) return null
        return content.substring(node.startOffset, closeStart + "</details>".length)
    }

    /** 预处理后的合成块：两个 base64 载荷都必须解码成功，否则视为不可识别。 */
    private fun parseSynthetic(text: String): HtmlDetailsData? =
        decodedPayload(text, SYNTHETIC_SUMMARY)?.let { summary ->
            decodedPayload(text, SYNTHETIC_BODY)?.let { body ->
                HtmlDetailsData(summary = summary.trim(), body = body.trim())
            }
        }

    private fun decodedPayload(
        text: String,
        pattern: Regex,
    ): String? {
        val group = pattern.find(text) ?: return null
        return NativeMarkdownPreprocessor.decodeDetailPayload(group.groupValues[1])
    }
}
