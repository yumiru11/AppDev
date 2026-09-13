package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDimensTest {
    @Test
    fun spacing_matchesPlanScale_hasSixFourBasedSteps() {
        assertEquals(4.dp, AppDimens.spacing.xs)
        assertEquals(8.dp, AppDimens.spacing.s)
        assertEquals(12.dp, AppDimens.spacing.m)
        assertEquals(16.dp, AppDimens.spacing.l)
        assertEquals(24.dp, AppDimens.spacing.xl)
        assertEquals(32.dp, AppDimens.spacing.xxl)
    }

    @Test
    fun spacing_orderedAscending_noDuplicateOrInvertedSteps() {
        val steps =
            listOf(
                AppDimens.spacing.xs,
                AppDimens.spacing.s,
                AppDimens.spacing.m,
                AppDimens.spacing.l,
                AppDimens.spacing.xl,
                AppDimens.spacing.xxl,
            )
        assertTrue(steps.zipWithNext().all { (smaller, larger) -> smaller.value < larger.value })
    }

    @Test
    fun contentPadding_aliasEqualsSpacingLarge_legacyCallSitesSeeSameValue() {
        assertEquals(AppDimens.spacing.l, AppDimens.contentPadding)
    }

    @Test
    fun legacyConstants_retainDocumentedValues() {
        assertEquals(48.dp, AppDimens.minTouchTarget)
        assertEquals(96.dp, AppDimens.fabContentClearance)
    }
}
