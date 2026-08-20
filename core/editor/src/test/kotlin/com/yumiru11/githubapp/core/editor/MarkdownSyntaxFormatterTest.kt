package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 工具栏语法应用测试（plan.md §7.1，T21 验收第 1 条）。
 *
 * 覆盖每种动作的：有选区包裹 / 无选区插入 / 多行前缀 / 空文本 / 选区边界。
 */
class MarkdownSyntaxFormatterTest {
    // ---- 包裹类：加粗 ----

    @Test
    fun apply_boldWithSelection_wrapsSelectionAndKeepsItSelected() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.BOLD, "hello world", 0, 5)

        assertEquals("**hello** world", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun apply_boldNoSelection_placesCursorBetweenMarkers() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.BOLD, "hello", 2, 2)

        assertEquals("he****llo", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(4, result.selectionEnd)
    }

    @Test
    fun apply_boldEmptyText_placesCursorBetweenMarkers() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.BOLD, "", 0, 0)

        assertEquals("****", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }

    // ---- 包裹类：斜体 ----

    @Test
    fun apply_italicWithSelection_wrapsWithSingleAsterisk() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.ITALIC, "hello world", 0, 5)

        assertEquals("*hello* world", result.text)
        assertEquals(1, result.selectionStart)
        assertEquals(6, result.selectionEnd)
    }

    @Test
    fun apply_italicNoSelection_placesCursorBetweenMarkers() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.ITALIC, "hello", 2, 2)

        assertEquals("he**llo", result.text)
        assertEquals(3, result.selectionStart)
        assertEquals(3, result.selectionEnd)
    }

    // ---- 包裹类：行内代码 ----

    @Test
    fun apply_inlineCodeWithSelection_wrapsWithBackticks() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.INLINE_CODE, "use foo() here", 4, 9)

        assertEquals("use `foo()` here", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(10, result.selectionEnd)
    }

    @Test
    fun apply_inlineCodeNoSelection_placesCursorBetweenBackticks() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.INLINE_CODE, "abc", 1, 1)

        assertEquals("a``bc", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }

    // ---- 代码块 ----

    @Test
    fun apply_codeBlockWithSelection_wrapsInFencesAndPlacesCursorAfter() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.CODE_BLOCK, "val x = 1", 0, 9)

        assertEquals("```\nval x = 1\n```", result.text)
        assertEquals(17, result.selectionStart)
        assertEquals(17, result.selectionEnd)
    }

    @Test
    fun apply_codeBlockNoSelection_placesCursorInsideFences() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.CODE_BLOCK, "abc", 1, 1)

        assertEquals("a```\n\n```bc", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    // ---- 标题 ----

    @Test
    fun apply_headingOnCurrentLine_prefixesLine() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.HEADING, "hello", 0, 0)

        assertEquals("# hello", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }

    @Test
    fun apply_headingWithMultiLineSelection_prefixesEveryLine() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.HEADING, "line1\nline2\nline3", 0, 13)

        assertEquals("# line1\n# line2\n# line3", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(19, result.selectionEnd)
    }

    @Test
    fun apply_headingSelectionInMiddleOfLine_prefixesLineAndShiftsSelection() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.HEADING, "hello world", 6, 11)

        assertEquals("# hello world", result.text)
        assertEquals(8, result.selectionStart)
        assertEquals(13, result.selectionEnd)
    }

    @Test
    fun apply_headingEmptyText_prefixesEmptyLine() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.HEADING, "", 0, 0)

        assertEquals("# ", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }

    // ---- 无序列表 ----

    @Test
    fun apply_unorderedListWithSelection_prefixesEachLine() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.UNORDERED_LIST, "a\nb\nc", 0, 5)

        assertEquals("- a\n- b\n- c", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    // ---- 有序列表 ----

    @Test
    fun apply_orderedListWithSelection_prefixesEachLineWithNumber() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.ORDERED_LIST, "a\nb", 0, 3)

        assertEquals("1. a\n1. b", result.text)
        assertEquals(3, result.selectionStart)
        assertEquals(9, result.selectionEnd)
    }

    // ---- 任务列表 ----

    @Test
    fun apply_taskListWithSelection_prefixesEachLineWithCheckbox() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.TASK_LIST, "a\nb", 0, 3)

        assertEquals("- [ ] a\n- [ ] b", result.text)
        assertEquals(6, result.selectionStart)
        assertEquals(15, result.selectionEnd)
    }

    // ---- 引用 ----

    @Test
    fun apply_quoteWithSelection_prefixesEachLine() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.QUOTE, "a\nb", 0, 3)

        assertEquals("> a\n> b", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun apply_quoteNoSelectionOnSecondLine_prefixesOnlyThatLine() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.QUOTE, "a\nb", 2, 2)

        assertEquals("a\n> b", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(4, result.selectionEnd)
    }

    // ---- 链接 ----

    @Test
    fun apply_linkWithSelection_usesSelectionAsTextAndSelectsUrlPlaceholder() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.LINK, "click here", 0, 5)

        assertEquals("[click](url) here", result.text)
        assertEquals(8, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun apply_linkNoSelection_placesCursorInUrlPlaceholder() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.LINK, "abc", 1, 1)

        assertEquals("a[](url)bc", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    // ---- 图片 ----

    @Test
    fun apply_imageWithSelection_usesSelectionAsAltAndSelectsUrlPlaceholder() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.IMAGE, "logo", 0, 4)

        assertEquals("![logo](url)", result.text)
        assertEquals(8, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun apply_imageNoSelection_placesCursorInUrlPlaceholder() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.IMAGE, "abc", 1, 1)

        assertEquals("a![](url)bc", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(8, result.selectionEnd)
    }

    // ---- 选区边界防御 ----

    @Test
    fun apply_selectionOutOfBounds_clampsToTextLength() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.BOLD, "hi", 5, 10)

        assertEquals("hi****", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(4, result.selectionEnd)
    }

    @Test
    fun apply_reversedSelection_normalizesToForwardRange() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.BOLD, "hello", 5, 0)

        assertEquals("**hello**", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun apply_selectionEndingAtNewline_prefixesOnlyLinesWithContent() {
        val result = MarkdownSyntaxFormatter.apply(MarkdownToolbarAction.HEADING, "a\nb", 0, 2)

        assertEquals("# a\nb", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(4, result.selectionEnd)
    }
}
