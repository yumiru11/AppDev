package com.yumiru11.githubapp.feature.search.qualifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * qualifier 建议逻辑单测（T18 验收第 2 条：qualifier 快速建议）。
 *
 * 覆盖：静态清单内容、appendQualifier 的空输入/已含 qualifier 去重/正常追加/空白边缘。
 */
class SearchQualifierTest {
    @Test
    fun suggestions_containsCommonQualifiers() {
        val values = QUALIFIER_SUGGESTIONS.map { it.value }

        assertTrue(values.contains("is:issue"))
        assertTrue(values.contains("is:pr"))
        assertTrue(values.contains("language:kotlin"))
        assertTrue(values.contains("stars:>100"))
        assertTrue(values.contains("user:"))
        assertTrue("label 必须与 value 一致（chip 展示即 qualifier 本身）", QUALIFIER_SUGGESTIONS.all { it.label == it.value })
    }

    @Test
    fun appendQualifier_emptyInput_returnsQualifierOnly() {
        assertEquals("language:kotlin", appendQualifier("", "language:kotlin"))
        assertEquals("language:kotlin", appendQualifier("   ", "language:kotlin"))
    }

    @Test
    fun appendQualifier_inputContainsQualifier_returnsInputUnchanged() {
        assertEquals("foo language:kotlin", appendQualifier("foo language:kotlin", "language:kotlin"))
        assertEquals("language:kotlin foo", appendQualifier("language:kotlin foo", "language:kotlin"))
    }

    @Test
    fun appendQualifier_normalInput_appendsWithSpace() {
        assertEquals("foo language:kotlin", appendQualifier("foo", "language:kotlin"))
        assertEquals("foo language:kotlin", appendQualifier("  foo  ", "language:kotlin"))
    }

    @Test
    fun appendQualifier_userPrefix_supportsPartialQualifier() {
        assertEquals("foo user:", appendQualifier("foo", "user:"))
    }
}
