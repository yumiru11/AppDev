package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlDetailsParserTest {
    @Test
    fun parse_detailsBlock_extractsSummaryAndBody() {
        val md = "<details>\n<summary>Open me</summary>\n\nBody **content**.\n</details>\n"
        val node =
            MarkdownParser(GFMFlavourDescriptor())
                .buildMarkdownTreeFromString(md)
                .children
                .first { it.type == MarkdownElementTypes.HTML_BLOCK }

        val data = HtmlDetailsParser.parse(md, node)

        assertEquals("Open me", data?.summary)
        assertEquals("Body **content**.", data?.body?.trim())
    }

    @Test
    fun parse_nonDetailsHtml_returnsNull() {
        val md = "<div>plain</div>\n"
        val node =
            MarkdownParser(GFMFlavourDescriptor())
                .buildMarkdownTreeFromString(md)
                .children
                .first { it.type == MarkdownElementTypes.HTML_BLOCK }

        assertNull(HtmlDetailsParser.parse(md, node))
    }
}
