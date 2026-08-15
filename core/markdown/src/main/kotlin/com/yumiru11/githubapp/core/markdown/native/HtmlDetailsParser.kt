package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

/** `<details>` HTML block 解析结果。 */
data class HtmlDetailsData(
    val summary: String,
    val body: String,
)

/**
 * `<details>` 原生折叠解析器（spike，纯函数）。
 *
 * mikepenz 0.38.1 没有 details 槽；HTML_BLOCK 经 `custom` 槽分发后用本类提取
 * summary 与正文。无法解析时返回 null，由调用方降级为原始文本。
 */
object HtmlDetailsParser {
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
        // GFM 会把 <details>/<summary> 与 </details> 拆成多个 HTML_BLOCK；
        // 只有首块包含 <details 时，才向后续内容借用到 </details> 的完整区间。
        val raw =
            if (nodeText.contains("<details", ignoreCase = true) && !nodeText.contains("</details>", ignoreCase = true)) {
                val closeStart = content.indexOf("</details>", node.endOffset, ignoreCase = true)
                if (closeStart < 0) return null
                content.substring(node.startOffset, closeStart + "</details>".length)
            } else {
                nodeText
            }
        val match = DETAILS_REGEX.find(raw) ?: return null
        return HtmlDetailsData(
            summary = match.groupValues[1].trim(),
            body = match.groupValues[2].trim(),
        )
    }
}
