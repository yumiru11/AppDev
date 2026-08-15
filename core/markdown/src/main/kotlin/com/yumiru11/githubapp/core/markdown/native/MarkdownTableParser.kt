package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/** 表格单元格：保留 AST node 用于行内 Markdown 渲染，text 用于测试与宽度估算。 */
data class MarkdownCell(
    val node: ASTNode,
    val text: String,
)

/** GFM 表格 AST 提取结果。 */
data class MarkdownTableData(
    val header: List<MarkdownCell>,
    val rows: List<List<MarkdownCell>>,
)

/**
 * GFM 表格 AST 解析器（纯函数，无 Compose/Android 依赖）。
 *
 * mikepenz 0.38.1 的 table 槽只给整个 TABLE node；本解析器从
 * [GFMElementTypes.HEADER] / [GFMElementTypes.ROW] 子节点中提取
 * [GFMTokenTypes.CELL]，供增强表格组件自绘。
 */
object MarkdownTableParser {
    fun parse(
        content: String,
        node: ASTNode,
    ): MarkdownTableData {
        // 用 type.name 匹配（GFM 与 core 的 MarkdownElementType 实例可能不同，
        // 仅判 GFMElementTypes.TABLE 在 mikepenz 表格槽下不命中 → 真机白屏，2026-08-15 验证）
        if (node.type.name != "TABLE") return MarkdownTableData(emptyList(), emptyList())

        val children = node.children
        val hasHeader = children.any { it.type.name == "HEADER" }
        val header =
            children
                .firstOrNull { it.type.name == "HEADER" || it.type.name == "ROW" }
                ?.cells(content)
                .orEmpty()
        val rowNodes =
            children.filter { it.type.name == "ROW" && (hasHeader || it != children.firstOrNull { t -> t.type.name == "ROW" }) }
        val rows = rowNodes.map { it.cells(content) }
        return MarkdownTableData(header = header, rows = rows)
    }

    private fun ASTNode.cells(content: String): List<MarkdownCell> =
        children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { cell ->
                MarkdownCell(
                    node = cell,
                    text = content.substring(cell.startOffset, cell.endOffset),
                )
            }
}
