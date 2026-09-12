package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 渲染前预处理的纯函数回归（缺陷 #1 折叠正文重复 / #4 脚本正文残留）。
 *
 * 纯 JVM（intellij-markdown + java.util.Base64），免 Robolectric。
 */
class NativeMarkdownPreprocessorTest {
    @Test
    fun prepare_blankLineDetails_form_producesSingleHtmlBlockWithDecodedPayload() {
        val markdown =
            "<details>\n<summary>Open me</summary>\n\nBODY_ONCE_TEXT\n\n</details>\n"

        val prepared = NativeMarkdownPreprocessor.prepare(markdown)

        val blocks = htmlBlocks(prepared)
        assertEquals("details 区域必须折叠为唯一一个 HTML_BLOCK", 1, blocks.size)
        val data = HtmlDetailsParser.parse(prepared, blocks.first())
        assertNotNull("合成块必须能被 HtmlDetailsParser 识别", data)
        assertEquals("Open me", data!!.summary)
        assertEquals("BODY_ONCE_TEXT", data.body)
        assertFalse("正文不得以原文残留在外层内容里", prepared.contains("BODY_ONCE_TEXT"))
    }

    @Test
    fun prepare_noBlankLineDetails_form_producesSingleHtmlBlock() {
        val markdown = "<details>\n<summary>Closed</summary>\nBODY_ONCE_TEXT\n</details>\n"

        val prepared = NativeMarkdownPreprocessor.prepare(markdown)

        val blocks = htmlBlocks(prepared)
        assertEquals(1, blocks.size)
        val data = HtmlDetailsParser.parse(prepared, blocks.first())
        assertEquals("Closed", data?.summary)
        assertEquals("BODY_ONCE_TEXT", data?.body)
    }

    @Test
    fun prepare_detailsWithMarkdownBody_keepsMarkdownForNestedParse() {
        val markdown = "<details>\n<summary>Summary</summary>\n\nBody **bold** and `code`.\n\n</details>\n"

        val prepared = NativeMarkdownPreprocessor.prepare(markdown)

        val data = HtmlDetailsParser.parse(prepared, htmlBlocks(prepared).first())
        assertEquals("Body **bold** and `code`.", data?.body)
    }

    @Test
    fun prepare_multipleDetails_collapsesEveryRegion() {
        val markdown =
            "<details>\n<summary>A</summary>\n\nFIRST_BODY_TEXT\n\n</details>\n\n" +
                "<details>\n<summary>B</summary>\n\nSECOND_BODY_TEXT\n\n</details>\n"

        val prepared = NativeMarkdownPreprocessor.prepare(markdown)

        val blocks = htmlBlocks(prepared)
        assertEquals(2, blocks.size)
        assertEquals("A", HtmlDetailsParser.parse(prepared, blocks[0])?.summary)
        assertEquals("FIRST_BODY_TEXT", HtmlDetailsParser.parse(prepared, blocks[0])?.body)
        assertEquals("B", HtmlDetailsParser.parse(prepared, blocks[1])?.summary)
        assertEquals("SECOND_BODY_TEXT", HtmlDetailsParser.parse(prepared, blocks[1])?.body)
    }

    @Test
    fun prepare_detailsInsideFencedCode_isLeftUntouched() {
        val markdown = "```html\n<details>\n<summary>X</summary>\n\n示例正文\n\n</details>\n```\n"

        assertEquals(markdown, NativeMarkdownPreprocessor.prepare(markdown))
    }

    @Test
    fun prepare_inlineScript_dropsElementContent() {
        val markdown = "before <script>alert(1)</script> after"

        assertEquals("before  after", NativeMarkdownPreprocessor.prepare(markdown))
    }

    @Test
    fun prepare_inlineScriptWithAttributes_dropsElementContent() {
        val markdown = "x<script type=\"text/javascript\" src=\"a.js\">alert(2)</script>y"

        assertEquals("xy", NativeMarkdownPreprocessor.prepare(markdown))
    }

    @Test
    fun prepare_scriptInsideFencedCode_isLeftUntouched() {
        val markdown = "```html\n<script>alert(1)</script>\n```\n"

        assertEquals(markdown, NativeMarkdownPreprocessor.prepare(markdown))
    }

    @Test
    fun prepare_scriptInsideDetailsBody_isDroppedBeforeEncoding() {
        val markdown = "<details>\n<summary>X</summary>\n\n<script>alert(1)</script>\n\n</details>\n"

        val prepared = NativeMarkdownPreprocessor.prepare(markdown)

        val data = HtmlDetailsParser.parse(prepared, htmlBlocks(prepared).first())
        assertEquals("", data?.body)
        assertFalse(prepared.contains("alert"))
    }

    @Test
    fun prepare_plainMarkdown_isIdentity() {
        val markdown = "# Title\n\nplain paragraph with `code`\n"

        assertEquals(markdown, NativeMarkdownPreprocessor.prepare(markdown))
    }

    @Test
    fun encodeAndDecodeDetailPayload_roundTripsUnicodeAndMarkdown() {
        val value = "标题：**粗体** <kbd>Ctrl</kbd>\n第二行"

        val decoded = NativeMarkdownPreprocessor.decodeDetailPayload(NativeMarkdownPreprocessor.encodeDetailPayload(value))

        assertEquals(value, decoded)
    }

    private fun htmlBlocks(markdown: String): List<ASTNode> {
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
        val nodes = mutableListOf<ASTNode>()
        collect(root, nodes)
        return nodes.sortedBy { it.startOffset }
    }

    private fun collect(
        node: ASTNode,
        out: MutableList<ASTNode>,
    ) {
        if (node.type == MarkdownElementTypes.HTML_BLOCK) out += node
        node.children.forEach { collect(it, out) }
    }
}
