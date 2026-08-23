package com.yumiru11.githubapp.feature.pullrequest.data

import com.yumiru11.githubapp.feature.pullrequest.model.DiffLineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DiffParser 单测（纯 JVM）：hunk 行号推进、+/-/context/元信息分型、CRLF 剥离、
 * 空/畸形输入不崩、side-by-side 行配对。测试命名 methodName_scenario_expectedBehavior。
 */
class DiffParserTest {
    private fun patch(vararg lines: String): String = lines.joinToString("\n")

    private fun crlfPatch(vararg lines: String): String = lines.joinToString("\r\n")

    private val samplePatch =
        patch(
            "diff --git a/README.md b/README.md",
            "index 111..222 100644",
            "--- a/README.md",
            "+++ b/README.md",
            "@@ -1,5 +1,6 @@",
            " # App",
            "-old line",
            "+new line",
            " context",
            "+added tail",
            "\\\\ No newline at end of file",
        )

    @Test
    fun parse_typicalHunk_assignsKindsAndNumbers() {
        val lines = DiffParser.parse(samplePatch)

        assertEquals(11, lines.size)
        assertEquals(DiffLineKind.HEADER, lines[0].kind) // diff --git
        assertEquals(DiffLineKind.HEADER, lines[4].kind) // @@ hunk 头
        assertEquals(DiffLineKind.CONTEXT, lines[5].kind)
        assertEquals(1, lines[5].oldNumber)
        assertEquals(1, lines[5].newNumber)
        assertEquals(DiffLineKind.REMOVED, lines[6].kind)
        assertEquals(2, lines[6].oldNumber)
        assertNull(lines[6].newNumber)
        assertEquals(DiffLineKind.ADDED, lines[7].kind)
        assertEquals(2, lines[7].newNumber)
        assertNull(lines[7].oldNumber)
        assertEquals(DiffLineKind.CONTEXT, lines[8].kind)
        assertEquals(3, lines[8].oldNumber)
        assertEquals(3, lines[8].newNumber)
        assertEquals(DiffLineKind.ADDED, lines[9].kind)
        assertEquals(4, lines[9].newNumber)
        assertEquals(DiffLineKind.NO_NEWLINE, lines[10].kind)
    }

    @Test
    fun parse_blankOrNull_returnsEmpty() {
        assertTrue(DiffParser.parse(null).isEmpty())
        assertTrue(DiffParser.parse("").isEmpty())
        assertTrue(DiffParser.parse("   ").isEmpty())
    }

    @Test
    fun parse_crlfLines_stripsCarriageReturn() {
        val lines = DiffParser.parse(crlfPatch("@@ -1,1 +1,1 @@", "+hello"))

        assertEquals(2, lines.size)
        assertEquals("hello", lines[1].text)
        assertTrue(!lines[1].text.contains('\r'))
    }

    @Test
    fun parse_malformedHunkWithoutNumbers_treatsAsHeaderWithoutCrash() {
        val lines = DiffParser.parse(patch("@@ no numbers @@", "+line"))

        assertEquals(DiffLineKind.HEADER, lines[0].kind)
        assertEquals(DiffLineKind.ADDED, lines[1].kind)
        assertNull(lines[1].newNumber)
    }

    @Test
    fun parse_hunkWithZeroCounts_resetsNumbers() {
        val lines = DiffParser.parse(patch("@@ -0,0 +3,1 @@", "+new file line"))

        assertEquals(3, lines[1].newNumber)
        assertNull(lines[1].oldNumber)
    }

    @Test
    fun toSideRows_removedThenAdded_pairsIntoOneRow() {
        val lines = DiffParser.parse(patch("@@ -1,2 +1,1 @@", "-old", "+new"))

        val rows = DiffParser.toSideRows(lines)

        val pairRow = rows.last()
        assertEquals("old", pairRow.old?.text)
        assertEquals("new", pairRow.new?.text)
    }

    @Test
    fun toSideRows_contextLine_appearsOnBothSides() {
        val lines = DiffParser.parse(patch("@@ -1,1 +1,1 @@", " ctx"))

        val rows = DiffParser.toSideRows(lines)

        val contextRow = rows.last()
        assertEquals(DiffLineKind.CONTEXT, contextRow.old?.kind)
        assertEquals(DiffLineKind.CONTEXT, contextRow.new?.kind)
    }

    @Test
    fun toSideRows_unpairedRemoval_hasNullNewSide() {
        val lines = DiffParser.parse(patch("@@ -1,2 +1,1 @@", "-old", "-old2"))

        val rows = DiffParser.toSideRows(lines)

        assertNull(rows.last().new)
        assertEquals("old2", rows.last().old?.text)
    }

    @Test
    fun toSideRows_headerRow_hasNullNewSide() {
        val rows = DiffParser.toSideRows(DiffParser.parse(samplePatch))

        assertEquals(DiffLineKind.HEADER, rows.first().old?.kind)
        assertNull(rows.first().new)
    }
}
