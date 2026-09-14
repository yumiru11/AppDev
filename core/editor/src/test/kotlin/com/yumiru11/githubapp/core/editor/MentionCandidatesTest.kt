package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `@mention` 候选归一化（SPEC-3）纯逻辑测试。
 *
 * 覆盖：空/空白/null 过滤、大小写不敏感去重（保留首次写法）、大小写不敏感排序、
 * 上限截断、非法上限防御。这些是「喂给 composer 的真实数据」的契约——
 * 补全面板本身的匹配/插入由 [MarkdownCompletionProviderTest] 与本模块 UI 测试守。
 */
class MentionCandidatesTest {
    @Test
    fun assembleMentionCandidates_duplicatesIgnoringCase_keepsFirstSpellingAndDropsRest() {
        val result = assembleMentionCandidates(listOf("Octocat", "octocat", "OCTOCAT"))

        assertEquals(listOf("Octocat"), result)
    }

    @Test
    fun assembleMentionCandidates_nullBlankAndWhitespaceOnlyEntries_areDropped() {
        val result = assembleMentionCandidates(listOf(null, "", "   ", "octocat", null))

        assertEquals(listOf("octocat"), result)
    }

    @Test
    fun assembleMentionCandidates_paddedLogins_areTrimmed() {
        val result = assembleMentionCandidates(listOf("  octocat  ", "\ttorvalds\n"))

        assertEquals(listOf("octocat", "torvalds"), result)
    }

    @Test
    fun assembleMentionCandidates_unsortedInput_sortsCaseInsensitively() {
        val result = assembleMentionCandidates(listOf("torvalds", "Alice", "bob", "octocat"))

        assertEquals(listOf("Alice", "bob", "octocat", "torvalds"), result)
    }

    @Test
    fun assembleMentionCandidates_sameLoginDifferentSpelling_keepsInsertionOrderAmongEqualKeys() {
        val result = assembleMentionCandidates(listOf("bravo", "alpha", "Bravo"))

        // 大小写不敏感比较下 bravo == Bravo → 稳定排序保留先出现的 bravo
        assertEquals(listOf("alpha", "bravo"), result)
    }

    @Test
    fun assembleMentionCandidates_moreThanLimit_truncatesAfterSorting() {
        val logins = (1..80).map { "user%02d".format(it) }

        val result = assembleMentionCandidates(logins)

        assertEquals(MAX_MENTION_CANDIDATES, result.size)
        // 截断发生在排序之后：保留字典序最小的 50 个
        assertEquals("user01", result.first())
        assertEquals("user50", result.last())
    }

    @Test
    fun assembleMentionCandidates_explicitSmallLimit_takesAlphabeticalHead() {
        val result = assembleMentionCandidates(listOf("c", "a", "b"), limit = 2)

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun assembleMentionCandidates_nonPositiveLimit_returnsEmpty() {
        assertTrue(assembleMentionCandidates(listOf("octocat"), limit = 0).isEmpty())
        assertTrue(assembleMentionCandidates(listOf("octocat"), limit = -1).isEmpty())
    }

    @Test
    fun assembleMentionCandidates_emptyInput_returnsEmpty() {
        assertTrue(assembleMentionCandidates(emptyList()).isEmpty())
    }
}
