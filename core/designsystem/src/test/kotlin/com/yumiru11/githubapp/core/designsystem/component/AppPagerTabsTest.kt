package com.yumiru11.githubapp.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 折叠头与 FAB 可见性的**纯函数内核**（仓库页收起、滑动感知 FAB）。
 *
 * 红→绿验证：把 [headerCollapseFraction] 的 coerceIn 去掉，越界断言失败；
 * 把 [fabVisibleAfterIdleMillis] 的阈值比较改成恒 true，"299ms 仍隐藏"断言失败。
 */
class AppPagerTabsTest {
    @Test
    fun headerCollapseFraction_zeroScroll_isExpanded() {
        assertEquals(0f, headerCollapseFraction(scrollPx = 0, collapseDistancePx = 100), 0.0001f)
    }

    @Test
    fun headerCollapseFraction_halfway_isHalfCollapsed() {
        assertEquals(0.5f, headerCollapseFraction(scrollPx = 50, collapseDistancePx = 100), 0.0001f)
    }

    @Test
    fun headerCollapseFraction_beyondDistance_clampsToOne() {
        assertEquals(1f, headerCollapseFraction(scrollPx = 500, collapseDistancePx = 100), 0.0001f)
    }

    @Test
    fun headerCollapseFraction_nonPositiveDistance_isExpanded() {
        assertEquals(0f, headerCollapseFraction(scrollPx = 200, collapseDistancePx = 0), 0.0001f)
    }

    @Test
    fun collapsedHeaderHeight_halvesAtHalfCollapse() {
        assertEquals(100, collapsedHeaderHeight(naturalHeightPx = 200, collapseFraction = 0.5f))
        assertEquals(0, collapsedHeaderHeight(naturalHeightPx = 200, collapseFraction = 1.2f))
        assertEquals(200, collapsedHeaderHeight(naturalHeightPx = 200, collapseFraction = -0.5f))
    }

    @Test
    fun collapsedHeaderDrift_movesUpProportionally() {
        assertEquals(0, collapsedHeaderDrift(naturalHeightPx = 200, collapseFraction = 0f))
        assertEquals(-50, collapsedHeaderDrift(naturalHeightPx = 200, collapseFraction = 1f, drift = 0.25f))
    }

    @Test
    fun fabVisibleAfterIdle_beforeDelay_staysHidden() {
        assertEquals(false, fabVisibleAfterIdleMillis(elapsedMillis = 0))
        assertEquals(false, fabVisibleAfterIdleMillis(elapsedMillis = 299))
    }

    @Test
    fun fabVisibleAfterIdle_atDelay_becomesVisible() {
        assertEquals(true, fabVisibleAfterIdleMillis(elapsedMillis = 300))
        assertEquals(true, fabVisibleAfterIdleMillis(elapsedMillis = 1000))
    }
}
