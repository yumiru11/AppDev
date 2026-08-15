package com.yumiru11.githubapp.core.markdown.native

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
        if (node.type != GFMElementTypes.TABLE) return MarkdownTableData(emptyList(), emptyList())

        val header =
            node.children
                .firstOrNull { it.type == GFMElementTypes.HEADER }
                ?.cells(content)
                .orEmpty()
        val rows =
            node.children
                .filter { it.type == GFMElementTypes.ROW }
                .map { it.cells(content) }
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
