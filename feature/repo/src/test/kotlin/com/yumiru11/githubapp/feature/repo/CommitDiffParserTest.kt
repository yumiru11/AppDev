package com.yumiru11.githubapp.feature.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CommitDiffParser] 单测（L09）。
 *
 * 覆盖：null/空白 patch → 空列表、hunk 头行号推进、新增/删除/上下文行号分配、
 * CRLF 容错、畸形 hunk 头（缺行号）不崩溃、二进制元信息行归入 HEADER、
 * "\ No newline at end of file" 标记。
 */
class CommitDiffParserTest {
    @Test
    fun parse_nullPatch_returnsEmptyList() {
        assertTrue(CommitDiffParser.parse(null).isEmpty())
        assertTrue(CommitDiffParser.parse("").isEmpty())
        assertTrue(CommitDiffParser.parse("   ").isEmpty())
    }

    @Test
    fun parse_singleHunk_assignsLineNumbers() {
        val patch =
            """
            @@ -1,3 +1,4 @@
             context
            -removed
            +added
            +added2
            """.trimIndent()

        val lines = CommitDiffParser.parse(patch)

        assertEquals(CommitDiffLineKind.HEADER, lines[0].kind)
        assertEquals("@@ -1,3 +1,4 @@", lines[0].text)
        assertNull(lines[0].oldNumber)

        assertEquals(CommitDiffLineKind.CONTEXT, lines[1].kind)
        assertEquals(1, lines[1].oldNumber)
        assertEquals(1, lines[1].newNumber)
        assertEquals("context", lines[1].text)

        assertEquals(CommitDiffLineKind.REMOVED, lines[2].kind)
        assertEquals(2, lines[2].oldNumber)
        assertNull(lines[2].newNumber)
        assertEquals("removed", lines[2].text)

        assertEquals(CommitDiffLineKind.ADDED, lines[3].kind)
        assertNull(lines[3].oldNumber)
        assertEquals(2, lines[3].newNumber)
        assertEquals("added", lines[3].text)

        assertEquals(CommitDiffLineKind.ADDED, lines[4].kind)
        assertEquals(3, lines[4].newNumber)
    }

    @Test
    fun parse_multipleHunks_resetsLineNumbersPerHunk() {
        val patch =
            """
            @@ -10,1 +10,1 @@
            -old
            +new
            @@ -50,1 +50,1 @@
            -old2
            +new2
            """.trimIndent()

        val lines = CommitDiffParser.parse(patch)

        val secondHunkRemoved = lines.first { it.kind == CommitDiffLineKind.REMOVED && it.text == "old2" }
        assertEquals(50, secondHunkRemoved.oldNumber)
        val secondHunkAdded = lines.first { it.kind == CommitDiffLineKind.ADDED && it.text == "new2" }
        assertEquals(50, secondHunkAdded.newNumber)
    }

    @Test
    fun parse_crlfLineEndings_stripsCarriageReturn() {
        val patch = "@@ -1,1 +1,1 @@\r\n-old\r\n+new\r\n"

        val lines = CommitDiffParser.parse(patch)

        assertEquals(CommitDiffLineKind.ADDED, lines[2].kind)
        assertEquals("new", lines[2].text)
    }

    @Test
    fun parse_malformedHunkHeader_doesNotCrashAndKeepsEditLines() {
        val patch =
            """
            @@ malformed @@
            +added
            """.trimIndent()

        val lines = CommitDiffParser.parse(patch)

        assertEquals(CommitDiffLineKind.HEADER, lines[0].kind)
        assertEquals(CommitDiffLineKind.ADDED, lines[1].kind)
        assertEquals("added", lines[1].text)
        assertNull("畸形 hunk 头无法推进行号", lines[1].newNumber)
    }

    @Test
    fun parse_metadataLines_classifiedAsHeader() {
        val patch =
            """
            diff --git a/a.kt b/a.kt
            index 11111..22222 100644
            --- a/a.kt
            +++ b/a.kt
            Binary files a/logo.png and b/logo.png differ
            """.trimIndent()

        val lines = CommitDiffParser.parse(patch)

        assertEquals(5, lines.size)
        assertTrue(lines.all { it.kind == CommitDiffLineKind.HEADER })
    }

    @Test
    fun parse_noNewlineMarker_classifiedAsNoNewline() {
        val patch =
            """
            @@ -1,1 +1,1 @@
            -old
            \ No newline at end of file
            """.trimIndent()

        val lines = CommitDiffParser.parse(patch)

        assertEquals(CommitDiffLineKind.NO_NEWLINE, lines.last().kind)
        assertEquals("\\ No newline at end of file", lines.last().text)
    }

    @Test
    fun parse_hunkHeaderWithoutCounts_parsesStartNumbers() {
        val patch =
            """
            @@ -5 +7 @@
            -a
            +b
            """.trimIndent()

        val lines = CommitDiffParser.parse(patch)

        assertEquals(5, lines[1].oldNumber)
        assertEquals(7, lines[2].newNumber)
    }
}
