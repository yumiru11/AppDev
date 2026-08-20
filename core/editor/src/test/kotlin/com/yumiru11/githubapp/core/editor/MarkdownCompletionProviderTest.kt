package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @mention / emoji 引用自动补全测试（plan.md §7.1，T21 验收第 3 条）。
 *
 * 纯函数层验证：前缀触发判定、大小写不敏感匹配、空数据/空前缀防御。
 */
class MarkdownCompletionProviderTest {
    private val mentions = listOf("octocat", "torvalds", "defunkt")
    private val emojis =
        listOf(
            MarkdownEmoji("smile", "\uD83D\uDE04"),
            MarkdownEmoji("rocket", "\uD83D\uDE80"),
            MarkdownEmoji("+1", "\uD83D\uDC4D"),
        )

    // ---- @mention ----

    @Test
    fun completionsFor_atPrefix_matchesMentions() {
        val result = MarkdownCompletionProvider.completionsFor("@oct", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals("@octocat", result[0].label)
        assertEquals("@octocat", result[0].commitText)
        assertEquals(4, result[0].prefixLength)
    }

    @Test
    fun completionsFor_atPrefixCaseInsensitive_matchesMentions() {
        val result = MarkdownCompletionProvider.completionsFor("@TOR", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals("@torvalds", result[0].label)
    }

    @Test
    fun completionsFor_atPrefixNoMatch_returnsEmpty() {
        val result = MarkdownCompletionProvider.completionsFor("@zzz", mentions, emojis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun completionsFor_bareAt_returnsAllMentions() {
        val result = MarkdownCompletionProvider.completionsFor("@", mentions, emojis)

        assertEquals(3, result.size)
    }

    // ---- emoji ----

    @Test
    fun completionsFor_colonPrefix_matchesEmojiShortcodes() {
        val result = MarkdownCompletionProvider.completionsFor(":sm", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals(":smile:", result[0].label)
        assertEquals(":smile:", result[0].commitText)
        assertEquals(3, result[0].prefixLength)
    }

    @Test
    fun completionsFor_colonPrefixCaseInsensitive_matchesEmoji() {
        val result = MarkdownCompletionProvider.completionsFor(":ROCK", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals(":rocket:", result[0].label)
    }

    @Test
    fun completionsFor_colonPrefixMatchesNumericShortcode() {
        val result = MarkdownCompletionProvider.completionsFor(":+", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals(":+1:", result[0].label)
    }

    @Test
    fun completionsFor_bareColon_returnsAllEmojis() {
        val result = MarkdownCompletionProvider.completionsFor(":", mentions, emojis)

        assertEquals(3, result.size)
    }

    // ---- 防御 ----

    @Test
    fun completionsFor_emptyPrefix_returnsEmpty() {
        val result = MarkdownCompletionProvider.completionsFor("", mentions, emojis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun completionsFor_plainWord_returnsEmpty() {
        val result = MarkdownCompletionProvider.completionsFor("hello", mentions, emojis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun completionsFor_emptyMentionsAndEmojis_returnsEmpty() {
        val result = MarkdownCompletionProvider.completionsFor("@oct", emptyList(), emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun completionsFor_atInMiddleOfSentence_extractsTokenAfterWhitespace() {
        val result = MarkdownCompletionProvider.completionsFor("see @oct", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals("@octocat", result[0].label)
        assertEquals(4, result[0].prefixLength)
    }

    @Test
    fun completionsFor_colonInMiddleOfSentence_extractsTokenAfterWhitespace() {
        val result = MarkdownCompletionProvider.completionsFor("try :sm", mentions, emojis)

        assertEquals(1, result.size)
        assertEquals(":smile:", result[0].label)
        assertEquals(3, result[0].prefixLength)
    }
}
