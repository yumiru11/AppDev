package com.yumiru11.githubapp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * stagger 延迟纯函数契约（#89，UI17 上移到 core:ui 后原样保留）：
 * 24ms 步进、首屏窗口封顶、越界归零。
 */
class StaggerDelayTest {
    @Test
    fun staggerDelayMillis_leadingIndexes_stepByIntervalToken() {
        assertEquals(0, staggerDelayMillis(0))
        assertEquals(24, staggerDelayMillis(1))
        assertEquals(48, staggerDelayMillis(2))
    }

    @Test
    fun staggerDelayMillis_atWindowCap_isZero() {
        assertEquals(0, staggerDelayMillis(STAGGER_MAX_ITEMS))
        assertEquals(0, staggerDelayMillis(999))
    }

    @Test
    fun staggerDelayMillis_lastWindowItem_usesMaxDelay() {
        assertEquals((STAGGER_MAX_ITEMS - 1) * 24, staggerDelayMillis(STAGGER_MAX_ITEMS - 1))
    }

    @Test
    fun staggerDelayMillis_negativeIndex_isZero() {
        assertEquals(0, staggerDelayMillis(-1))
    }
}
