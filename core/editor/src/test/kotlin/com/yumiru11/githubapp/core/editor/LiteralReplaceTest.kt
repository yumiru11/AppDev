package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [LiteralSearch] 单测（EDITOR-1 replace-one / replace-all 的纯逻辑防线）。
 *
 * 语义基准 = Sora `TextUtils.indexOf`（`EditorSearcher` 命中集合的来源），保证
 * 「全部替换」的范围与查找高亮一致。
 */
class LiteralReplaceTest {
    // ── indexOf ────────────────────────────────────────────────────────────

    @Test
    fun indexOf_caseInsensitiveKeyMatchingDifferentCase_returnsIndex() {
        assertEquals(1, LiteralSearch.indexOf("aBc", "bc", caseInsensitive = true, fromIndex = 0))
    }

    @Test
    fun indexOf_caseSensitiveDifferentCase_returnsNoMatch() {
        assertEquals(-1, LiteralSearch.indexOf("aBc", "bc", caseInsensitive = false, fromIndex = 0))
    }

    @Test
    fun indexOf_fromIndexPastFirstMatch_findsLaterMatch() {
        assertEquals(4, LiteralSearch.indexOf("aXaXa", "a", caseInsensitive = true, fromIndex = 3))
    }

    @Test
    fun indexOf_queryLongerThanText_returnsNoMatch() {
        assertEquals(-1, LiteralSearch.indexOf("ab", "abc", caseInsensitive = true, fromIndex = 0))
    }

    @Test
    fun indexOf_emptyQuery_returnsNoMatch() {
        assertEquals(-1, LiteralSearch.indexOf("abc", "", caseInsensitive = true, fromIndex = 0))
    }

    // ── findAll ────────────────────────────────────────────────────────────

    @Test
    fun findAll_multipleOccurrences_returnsNonOverlappingMatchesInOrder() {
        val matches = LiteralSearch.findAll("a b a b a", "a", caseInsensitive = true)

        assertEquals(listOf(LiteralMatch(0, 1), LiteralMatch(4, 5), LiteralMatch(8, 9)), matches)
    }

    @Test
    fun findAll_overlappingPattern_returnsNonOverlappingMatches() {
        // Sora 扫描命中后跳过整个匹配长度 → "aaa" 里 "aa" 只有一处（偏移 0）
        val matches = LiteralSearch.findAll("aaa", "aa", caseInsensitive = true)

        assertEquals(listOf(LiteralMatch(0, 2)), matches)
    }

    @Test
    fun findAll_caseInsensitive_matchesAcrossCase() {
        val matches = LiteralSearch.findAll("Foo foo", "FOO", caseInsensitive = true)

        assertEquals(2, matches.size)
    }

    @Test
    fun findAll_emptyQuery_returnsEmptyList() {
        assertEquals(emptyList<LiteralMatch>(), LiteralSearch.findAll("abc", "", caseInsensitive = true))
    }

    // ── replaceAll ─────────────────────────────────────────────────────────

    @Test
    fun replaceAll_multipleMatches_replacesAllAndReportsCount() {
        val plan = LiteralSearch.replaceAll("a b a b a", "a", "X", caseInsensitive = true)

        assertEquals("X b X b X", plan.text)
        assertEquals(3, plan.count)
    }

    @Test
    fun replaceAll_noMatch_returnsOriginalTextWithZeroCount() {
        val plan = LiteralSearch.replaceAll("abc", "z", "X", caseInsensitive = true)

        assertEquals("abc", plan.text)
        assertEquals(0, plan.count)
    }

    @Test
    fun replaceAll_replacementContainsQuery_doesNotRescanInsertedText() {
        val plan = LiteralSearch.replaceAll("a a", "a", "aa", caseInsensitive = true)

        assertEquals("aa aa", plan.text)
        assertEquals(2, plan.count)
    }

    @Test
    fun replaceAll_emptyReplacement_deletesMatches() {
        val plan = LiteralSearch.replaceAll("x-a-x", "a", "", caseInsensitive = true)

        assertEquals("x--x", plan.text)
        assertEquals(1, plan.count)
    }

    @Test
    fun replaceAll_caseSensitive_skipsDifferentCase() {
        val plan = LiteralSearch.replaceAll("Ab a", "a", "b", caseInsensitive = false)

        assertEquals("Ab b", plan.text)
        assertEquals(1, plan.count)
    }

    @Test
    fun replaceAll_matchAtLineEnd_keepsSurroundingNewlines() {
        val plan = LiteralSearch.replaceAll("foo\nbar", "bar", "baz", caseInsensitive = true)

        assertEquals("foo\nbaz", plan.text)
        assertEquals(1, plan.count)
    }

    // ── replaceOne / selectMatch ───────────────────────────────────────────

    @Test
    fun replaceOne_middleMatch_replacesOnlyThatMatch() {
        val text = "a b a b a"

        assertEquals("a b X b a", LiteralSearch.replaceOne(text, LiteralMatch(4, 5), "X"))
    }

    @Test
    fun selectMatch_selectionEqualsMatch_prefersThatMatch() {
        val matches = LiteralSearch.findAll("a b a", "a", caseInsensitive = true)

        assertEquals(LiteralMatch(4, 5), LiteralSearch.selectMatch(matches, selectionStart = 4, selectionEnd = 5))
    }

    @Test
    fun selectMatch_noSelectionAfterMatch_picksNextMatchAtOrAfterCursor() {
        val matches = LiteralSearch.findAll("a b a", "a", caseInsensitive = true)

        assertEquals(LiteralMatch(4, 5), LiteralSearch.selectMatch(matches, selectionStart = 2, selectionEnd = 2))
    }

    @Test
    fun selectMatch_cursorAfterLastMatch_wrapsToFirst() {
        val matches = LiteralSearch.findAll("a b a", "a", caseInsensitive = true)

        assertEquals(LiteralMatch(0, 1), LiteralSearch.selectMatch(matches, selectionStart = 5, selectionEnd = 5))
    }

    @Test
    fun selectMatch_noMatches_returnsNull() {
        assertNull(LiteralSearch.selectMatch(emptyList(), selectionStart = 0, selectionEnd = 0))
    }
}
