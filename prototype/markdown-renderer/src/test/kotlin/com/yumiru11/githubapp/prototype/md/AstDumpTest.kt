package com.yumiru11.githubapp.prototype.md

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Test

class AstDumpTest {

    private fun dump(node: org.intellij.markdown.ast.ASTNode, depth: Int) {
        val indent = "  ".repeat(depth)
        println("$indent${node.type} [${node.startOffset},${node.endOffset}]")
        node.children.forEach { dump(it, depth + 1) }
    }

    @Test
    fun dumpTaskListAst() {
        val md = "- [x] 已复现\n- [ ] 已定位根因\n"
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(md)
        dump(tree, 0)
    }

    @Test
    fun dumpFullSampleAAst() {
        val md = """
        ## 复现步骤：列表滚动时偶发卡顿

        在 **快速滚动** 时，`PullToRefreshBox` 偶发回弹，怀疑与 #42 相关。

        > 环境：Pixel 8 / Android 15 / 屏幕刷新率 120Hz
        > 版本：v0.1.0

        1. 打开首页
        2. 快速下滑再上滑
        3. 观察回弹

        - [x] 已复现
        - [ ] 已定位根因

        @yumir11 有空看一下吗？之前 GitLight 也踩过这个坑 :bug:
        """.trimIndent()
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(md)
        dump(tree, 0)
    }

    @Test
    fun dumpCheckboxTokenKind() {
        println("CHECK_BOX token type = ${org.intellij.markdown.flavours.gfm.GFMTokenTypes.CHECK_BOX}")
        println("LIST_ITEM = ${MarkdownElementTypes.LIST_ITEM}")
    }
}