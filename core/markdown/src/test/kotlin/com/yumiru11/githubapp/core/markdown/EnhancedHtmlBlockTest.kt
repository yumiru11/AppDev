package com.yumiru11.githubapp.core.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HTML 块降级文本化（stripHtmlTags）与徽章解析（HtmlBadgeParser.parseAll）测试。
 *
 * 纯函数，JVM 免 Robolectric。2026-08-17 真机修复波新增逻辑的覆盖（覆盖率门禁 0.42）。
 */
class EnhancedHtmlBlockTest {
    @Test
    fun stripHtmlTags_removesTagsAndDecodesEntities() {
        val html = "<p align=\"center\"><strong>EchoMusic</strong> —— 播放器</p>\n<p>a &amp; b &lt; c &gt; d</p>"

        val result = stripHtmlTags(html)

        assertEquals("EchoMusic —— 播放器\na & b < c > d", result)
    }

    @Test
    fun stripHtmlTags_collapsesExcessNewlines() {
        val html = "<p>one</p>\n\n\n\n<p>two</p>"

        val result = stripHtmlTags(html)

        assertEquals("one\n\ntwo", result)
    }

    @Test
    fun stripHtmlTags_emptyHtml_returnsEmpty() {
        assertEquals("", stripHtmlTags(""))
    }

    @Test
    fun parseAll_multipleBadges_returnsAllHttpImages() {
        val content =
            "<p align=\"center\">\n" +
                "  <img src=\"https://img.shields.io/badge/Electron-42.3.1-blue\" alt=\"Electron\">\n" +
                "  <img src=\"https://img.shields.io/badge/Vue-3.5-brightgreen\" alt=\"Vue\">\n" +
                "</p>"
        val node = parseHtmlBlockNode(content)

        val badges =
            com.yumiru11.githubapp.core.markdown.native.HtmlBadgeParser
                .parseAll(content, node)

        assertEquals(2, badges.size)
        assertEquals("https://img.shields.io/badge/Electron-42.3.1-blue", badges[0].imageSrc)
        assertEquals(true, badges[0].alignCenter)
    }

    @Test
    fun parseAll_relativeImage_withBaseRepoUrl_resolvesRawUrl() {
        val content = "<p align=\"center\"><img src=\"build/icons/icon.png\" alt=\"Logo\"></p>"
        val node = parseHtmlBlockNode(content)

        val badges =
            com.yumiru11.githubapp.core.markdown.native.HtmlBadgeParser.parseAll(
                content,
                node,
                baseRepoUrl = "https://github.com/hoowhoami/EchoMusic",
            )

        assertEquals(1, badges.size)
        assertEquals(
            "https://raw.githubusercontent.com/hoowhoami/EchoMusic/HEAD/build/icons/icon.png",
            badges[0].imageSrc,
        )
    }

    @Test
    fun parseAll_relativeImage_withoutBaseRepoUrl_returnsEmpty() {
        val content = "<p><img src=\"docs/x.png\"></p>"
        val node = parseHtmlBlockNode(content)

        val badges =
            com.yumiru11.githubapp.core.markdown.native.HtmlBadgeParser
                .parseAll(content, node)

        assertEquals(0, badges.size)
    }

    @Test
    fun parseAll_noImages_returnsEmpty() {
        val content = "<p>just text</p>"
        val node = parseHtmlBlockNode(content)

        val badges =
            com.yumiru11.githubapp.core.markdown.native.HtmlBadgeParser
                .parseAll(content, node)

        assertEquals(0, badges.size)
    }

    /** 构造 HTML_BLOCK 节点（解析后取 HTML_BLOCK 子节点——DOCUMENT 根节点类型不符）。 */
    private fun parseHtmlBlockNode(content: String): org.intellij.markdown.ast.ASTNode {
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
        return root.children.first { it.type == MarkdownElementTypes.HTML_BLOCK }
    }
}
