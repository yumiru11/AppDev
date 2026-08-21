package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppShapesTest {
    @Test
    fun resolveCornerTokens_defaultScale_matchesAppDimensTokens() {
        val tokens = AppShapes.resolveCornerTokens(1f)
        assertEquals(4.dp, tokens.extraSmall)
        assertEquals(8.dp, tokens.small)
        assertEquals(12.dp, tokens.medium)
        assertEquals(16.dp, tokens.large)
        assertEquals(28.dp, tokens.extraLarge)
    }

    @Test
    fun resolveCornerTokens_minScale_halvesAllRadii() {
        val tokens = AppShapes.resolveCornerTokens(AppShapes.MIN_CORNER_SCALE)
        assertEquals(2.dp, tokens.extraSmall)
        assertEquals(4.dp, tokens.small)
        assertEquals(6.dp, tokens.medium)
        assertEquals(8.dp, tokens.large)
        assertEquals(14.dp, tokens.extraLarge)
    }

    @Test
    fun resolveCornerTokens_maxScale_scalesAllRadiiUp() {
        val tokens = AppShapes.resolveCornerTokens(AppShapes.MAX_CORNER_SCALE)
        assertEquals(6.dp, tokens.extraSmall)
        assertEquals(12.dp, tokens.small)
        assertEquals(18.dp, tokens.medium)
        assertEquals(24.dp, tokens.large)
        assertEquals(42.dp, tokens.extraLarge)
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
    fun from_defaultScale_matchesMaterial3DefaultShapes() {
        val shapes = AppShapes.from(1f)
        assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), shapes.small)
        assertEquals(RoundedCornerShape(12.dp), shapes.medium)
        assertEquals(RoundedCornerShape(16.dp), shapes.large)
        assertEquals(RoundedCornerShape(28.dp), shapes.extraLarge)
    }

    @Test
    fun from_customScale_appliesScaledRadii() {
        val shapes = AppShapes.from(0.5f)
        assertEquals(RoundedCornerShape(2.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(6.dp), shapes.medium)
        assertEquals(RoundedCornerShape(14.dp), shapes.extraLarge)
    }
}
