package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileFindState] 单测（#166 / UI14 文件内查找状态机；纯逻辑，无 Android 依赖）。
 *
 * 覆盖：查询词变更 / 权威结果回灌（正常 · 无匹配 · 单匹配 · 越界）/ 环形跳转
 * （循环到底回到第一项 · 首项回绕到末项）/ 空查询词 / 无匹配时的空操作。
 */
class FileFindStateTest {
    @Test
    fun withQuery_nonEmptyQuery_resetsCountAndEntersSearching() {
        val loaded = FileFindState().withResults(matchCount = 7, currentMatchIndex = 3)

        val state = loaded.withQuery("fun")

        assertEquals("fun", state.query)
        assertTrue(state.hasQuery)
        assertTrue("新查询结果未到，不得沿用上一查询词计数", state.isSearching)
        assertEquals(0, state.matchCount)
        assertEquals(FileFindState.NO_MATCH, state.currentMatchIndex)
        assertEquals(0, state.matchOrdinal)
    }

    @Test
    fun withQuery_emptyQuery_clearsCountAndLeavesSearching() {
        val state = FileFindState().withResults(matchCount = 3, currentMatchIndex = 1).withQuery("")

        assertFalse("空查询词 = 不查找（宿主据此清除高亮）", state.hasQuery)
        assertFalse(state.isSearching)
        assertFalse(state.hasMatches)
        assertEquals(0, state.matchCount)
        assertEquals(0, state.matchOrdinal)
    }

    @Test
    fun withResults_noMatches_clearsCountAndOrdinal() {
        val state =
            FileFindState(query = "zzz").withResults(matchCount = 0, currentMatchIndex = FileFindState.NO_MATCH)

        assertFalse(state.hasMatches)
        assertFalse(state.isSearching)
        assertEquals(0, state.matchCount)
        assertEquals(0, state.matchOrdinal)
    }

    @Test
    fun withResults_singleMatch_selectsOnlyMatchAndKeepsItOnCycle() {
        val state = FileFindState(query = "fun").withResults(matchCount = 1, currentMatchIndex = 0)

        assertTrue(state.hasMatches)
        assertEquals(1, state.matchOrdinal)
        assertEquals(1, state.matchCount)
        assertEquals("单匹配时循环停在原处", state, state.cycledNext())
        assertEquals("单匹配时循环停在原处", state, state.cycledPrevious())
    }

    @Test
    fun withResults_negativeIndex_recordsNoMatch() {
        // Sora 搜索完成后不自动选中 → currentMatchedPositionIndex 为 -1，不得臆造为第 1 项
        val state = FileFindState(query = "fun").withResults(matchCount = 5, currentMatchIndex = FileFindState.NO_MATCH)

        assertTrue(state.hasMatches)
        assertEquals(5, state.matchCount)
        assertEquals(0, state.matchOrdinal)
    }

    @Test
    fun withResults_indexAtOrBeyondCount_recordsNoMatch() {
        val atCount = FileFindState(query = "fun").withResults(matchCount = 3, currentMatchIndex = 3)
        val beyondCount = FileFindState(query = "fun").withResults(matchCount = 3, currentMatchIndex = 9)

        assertEquals(0, atCount.matchOrdinal)
        assertEquals(0, beyondCount.matchOrdinal)
        assertEquals(3, beyondCount.matchCount)
    }

    @Test
    fun withResults_afterSearching_leavesSearchingFlag() {
        val state = FileFindState().withQuery("fun").withResults(matchCount = 2, currentMatchIndex = 0)

        assertFalse(state.isSearching)
        assertEquals(1, state.matchOrdinal)
        assertEquals(2, state.matchCount)
    }

    @Test
    fun cycledNext_lastMatch_wrapsToFirst() {
        val state = FileFindState(query = "fun").withResults(matchCount = 3, currentMatchIndex = 2)

        val next = state.cycledNext()

        assertEquals("末项之后回到第一项", 1, next.matchOrdinal)
        assertEquals(3, next.matchCount)
    }

    @Test
    fun cycledNext_walksForwardThroughAllMatches() {
        var state = FileFindState(query = "fun").withResults(matchCount = 3, currentMatchIndex = 0)

        val ordinals = mutableListOf<Int>()
        repeat(4) {
            state = state.cycledNext()
            ordinals += state.matchOrdinal
        }

        assertEquals("一轮循环：2 → 3 → 1 → 2", listOf(2, 3, 1, 2), ordinals)
    }

    @Test
    fun cycledPrevious_firstMatch_wrapsToLast() {
        val state = FileFindState(query = "fun").withResults(matchCount = 4, currentMatchIndex = 0)

        assertEquals("首项之前回到末项", 4, state.cycledPrevious().matchOrdinal)
    }

    @Test
    fun cycledNext_withoutSelection_selectsFirstMatch() {
        val state = FileFindState(query = "fun").withResults(matchCount = 5, currentMatchIndex = FileFindState.NO_MATCH)

        assertEquals(1, state.cycledNext().matchOrdinal)
    }

    @Test
    fun cycledPrevious_withoutSelection_selectsLastMatch() {
        val state = FileFindState(query = "fun").withResults(matchCount = 5, currentMatchIndex = FileFindState.NO_MATCH)

        assertEquals(5, state.cycledPrevious().matchOrdinal)
    }

    @Test
    fun cycledNext_noMatches_keepsStateUnchanged() {
        val state = FileFindState(query = "zzz").withResults(matchCount = 0, currentMatchIndex = FileFindState.NO_MATCH)

        assertEquals(state, state.cycledNext())
    }

    @Test
    fun cycledPrevious_noMatches_keepsStateUnchanged() {
        val state = FileFindState(query = "zzz").withResults(matchCount = 0, currentMatchIndex = FileFindState.NO_MATCH)

        assertEquals(state, state.cycledPrevious())
    }

    @Test
    fun defaultState_hasNoQueryNoMatchesAndNoOrdinal() {
        val state = FileFindState()

        assertFalse(state.hasQuery)
        assertFalse(state.hasMatches)
        assertFalse(state.isSearching)
        assertEquals(0, state.matchCount)
        assertEquals(0, state.matchOrdinal)
    }
}
