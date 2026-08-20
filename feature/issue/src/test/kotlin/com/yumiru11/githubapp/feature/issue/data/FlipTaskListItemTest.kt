package com.yumiru11.githubapp.feature.issue.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [flipTaskListItem] 单测（T14 任务列表反向同步的 markdown 解析）。
 *
 * 覆盖：无序/有序/嵌套任务项、勾选与取消、index 越界原样返回、非任务行不受影响。
 */
class FlipTaskListItemTest {
    @Test
    fun flipTaskListItem_checkFirstItem_flipsToChecked() {
        val markdown = "- [ ] task one\n- [x] task two"
        assertEquals("- [x] task one\n- [x] task two", flipTaskListItem(markdown, index = 0, checked = true))
    }

    @Test
    fun flipTaskListItem_uncheckSecondItem_flipsToUnchecked() {
        val markdown = "- [x] task one\n- [x] task two"
        assertEquals("- [x] task one\n- [ ] task two", flipTaskListItem(markdown, index = 1, checked = false))
    }

    @Test
    fun flipTaskListItem_orderedList_flipsNthItem() {
        val markdown = "1. [ ] first\n2. [x] second"
        assertEquals("1. [ ] first\n2. [ ] second", flipTaskListItem(markdown, index = 1, checked = false))
    }

    @Test
    fun flipTaskListItem_nestedIndentedItem_flipsInPlace() {
        val markdown = "- [x] parent\n  - [ ] child"
        assertEquals("- [x] parent\n  - [x] child", flipTaskListItem(markdown, index = 1, checked = true))
    }

    @Test
    fun flipTaskListItem_asteriskAndPlusMarkers_flips() {
        val markdown = "* [ ] a\n+ [x] b"
        assertEquals("* [x] a\n+ [x] b", flipTaskListItem(markdown, index = 0, checked = true))
    }

    @Test
    fun flipTaskListItem_indexOutOfRange_returnsOriginal() {
        val markdown = "- [ ] only"
        assertEquals(markdown, flipTaskListItem(markdown, index = 5, checked = true))
    }

    @Test
    fun flipTaskListItem_noTaskItems_returnsOriginal() {
        val markdown = "plain text\n- normal list item\n# heading"
        assertEquals(markdown, flipTaskListItem(markdown, index = 0, checked = true))
    }

    @Test
    fun flipTaskListItem_mixedContent_onlyFlipsTargetLine() {
        val markdown = "# Title\n\n- [ ] todo\n\nSome **bold** text\n\n- [x] done"
        val expected = "# Title\n\n- [x] todo\n\nSome **bold** text\n\n- [x] done"
        assertEquals(expected, flipTaskListItem(markdown, index = 0, checked = true))
    }

    @Test
    fun flipTaskListItem_uppercaseX_treatedAsChecked() {
        val markdown = "- [X] done"
        assertEquals("- [ ] done", flipTaskListItem(markdown, index = 0, checked = false))
    }

    @Test
    fun flipTaskListItem_emptyTextAfterCheckbox_preservesMarker() {
        val markdown = "- [ ]"
        assertEquals("- [x]", flipTaskListItem(markdown, index = 0, checked = true))
    }
}
