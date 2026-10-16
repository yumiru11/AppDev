package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 形状令牌 × cornerScale 的解析契约（数值与 [com.yumiru11.githubapp.core.designsystem.token.AppDimens] 同源）。 */
class AppShapesTest {
    @Test
    fun resolveCornerTokens_defaultScale_matchesAppDimensCornerTokens() {
        val tokens = AppShapes.resolveCornerTokens(1f)
        assertEquals(8.dp, tokens.extraSmall)
        assertEquals(12.dp, tokens.small)
        assertEquals(20.dp, tokens.medium)
        assertEquals(28.dp, tokens.large)
        assertEquals(36.dp, tokens.extraLarge)
    }

    @Test
    fun resolveCornerTokens_minScale_halvesAllRadii() {
        val tokens = AppShapes.resolveCornerTokens(AppShapes.MIN_CORNER_SCALE)
        assertEquals(4.dp, tokens.extraSmall)
        assertEquals(6.dp, tokens.small)
        assertEquals(10.dp, tokens.medium)
        assertEquals(14.dp, tokens.large)
        assertEquals(18.dp, tokens.extraLarge)
    }

    @Test
    fun resolveCornerTokens_maxScale_scalesAllRadiiUp() {
        val tokens = AppShapes.resolveCornerTokens(AppShapes.MAX_CORNER_SCALE)
        assertEquals(12.dp, tokens.extraSmall)
        assertEquals(18.dp, tokens.small)
        assertEquals(30.dp, tokens.medium)
        assertEquals(42.dp, tokens.large)
        assertEquals(54.dp, tokens.extraLarge)
    }

    @Test
    fun resolveCornerTokens_scaleBelowRange_clampedToMin() {
        assertEquals(
            AppShapes.resolveCornerTokens(AppShapes.MIN_CORNER_SCALE),
            AppShapes.resolveCornerTokens(0f),
        )
    }

    @Test
    fun resolveCornerTokens_scaleAboveRange_clampedToMax() {
        assertEquals(
            AppShapes.resolveCornerTokens(AppShapes.MAX_CORNER_SCALE),
            AppShapes.resolveCornerTokens(10f),
        )
    }

    @Test
    fun from_defaultScale_matchesDesignSystemCornerTokens() {
        val shapes = AppShapes.from(1f)
        assertEquals(RoundedCornerShape(8.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), shapes.small)
        assertEquals(RoundedCornerShape(20.dp), shapes.medium)
        assertEquals(RoundedCornerShape(28.dp), shapes.large)
        assertEquals(RoundedCornerShape(36.dp), shapes.extraLarge)
    }

    @Test
    fun from_customScale_appliesScaledRadii() {
        val shapes = AppShapes.from(0.5f)
        assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(10.dp), shapes.medium)
        assertEquals(RoundedCornerShape(18.dp), shapes.extraLarge)
    }
}
