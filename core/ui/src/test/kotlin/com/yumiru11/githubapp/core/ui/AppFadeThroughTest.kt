package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * M3 fade-through 过渡规格契约（#167 / UI16 + UI17，ui-design §4.3 渐入渐出）。
 *
 * 关键契约是**减弱动画的退化路径**：折算时长为 0（系统「移除动画」或设置滑杆到底）
 * 时必须返回 None 过渡 —— 直接切换，不进入动画路径（§4.4 约束）。
 */
class AppFadeThroughTest {
    @Test
    fun appFadeThroughTransform_zeroDurations_returnsNoneTransitions() {
        val transform = appFadeThroughTransform(enterMillis = 0, exitMillis = 0)

        assertEquals(EnterTransition.None, transform.targetContentEnter)
        assertEquals(ExitTransition.None, transform.initialContentExit)
    }

    @Test
    fun appFadeThroughTransform_negativeDurations_treatedAsZero() {
        val transform = appFadeThroughTransform(enterMillis = -10, exitMillis = -1)

        assertEquals(EnterTransition.None, transform.targetContentEnter)
        assertEquals(ExitTransition.None, transform.initialContentExit)
    }

    @Test
    fun appFadeThroughTransform_tokenDurations_returnsAnimatedTransitions() {
        // M3 官方 fade-through = 出场 90ms + 进场 210ms（AppMotion 令牌值）
        val transform = appFadeThroughTransform(enterMillis = 210, exitMillis = 90)

        assertNotEquals(EnterTransition.None, transform.targetContentEnter)
        assertNotEquals(ExitTransition.None, transform.initialContentExit)
    }
}
