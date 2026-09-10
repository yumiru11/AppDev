package com.yumiru11.githubapp.feature.home.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语言色点纯函数测试（L08）：同语言恒定同色 + 下标恒落在色板范围内。
 */
class TrendingSectionTest {
    @Test
    fun languageDotIndex_sameLanguage_isStableAcrossCalls() {
        assertEquals(languageDotIndex("Kotlin", 5), languageDotIndex("Kotlin", 5))
    }

    @Test
    fun languageDotIndex_anyLanguage_staysWithinPaletteBounds() {
        listOf("Kotlin", "Java", "Rust", "C++", "TypeScript", "", "Go").forEach { language ->
            val index = languageDotIndex(language, 5)
            assertTrue("语言 $language 的下标越界：$index", index in 0 until 5)
        }
    }
}
