package com.yumiru11.githubapp.core.markdown.native

import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableParserTest {
    @Test
    fun parse_tableFixture_extractsHeaderAndRows() {
        val md = "| A | B | C | D |\n|---|---|---|---|\n| 1 | 2 | **3** | `4` |\n| x | y | z | w |\n"
        val table = parseTable(md)

        val data = MarkdownTableParser.parse(md, table)

        assertEquals(4, data.header.size)
        assertEquals("A", data.header[0].text.trim())
        assertEquals(2, data.rows.size)
        assertEquals("`4`", data.rows[0][3].text.trim())
    }

    @Test
    fun parse_nodeWithoutTable_returnsEmptyData() {
        val md = "# heading\n"
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(md)

        val data = MarkdownTableParser.parse(md, root)

        assertEquals(0, data.header.size)
        assertEquals(0, data.rows.size)
    }

    private fun parseTable(md: String) =
        MarkdownParser(GFMFlavourDescriptor())
            .buildMarkdownTreeFromString(md)
            .children
            .first { it.type == GFMElementTypes.TABLE }
}
