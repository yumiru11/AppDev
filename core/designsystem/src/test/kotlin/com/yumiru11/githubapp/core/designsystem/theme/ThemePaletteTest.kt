package com.yumiru11.githubapp.core.designsystem.theme

import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for theme resolution.
 *
 * `ColorScheme` has no value equality (reference semantics), so palette
 * identity is asserted on [ExtendedColors] (data class) plus a
 * representative set of distinguishing [ColorScheme] slots.
 *
 * NOT unit-testable (annotated here so nobody tries):
 * - Real dynamic wallpaper extraction (`dynamicLightColorScheme` /
 *   `dynamicDarkColorScheme`): requires an Android 12+ wallpaper source;
 *   Robolectric provides none, so the extracted scheme would always be the
 *   stock fallback and the assertion would be meaningless.
 * - The composable fallback branch of [dynamicLightColors]/[dynamicDarkColors]
 *   below API 31: the SDK gate itself is tested via [supportsDynamicColors]
 *   with simulated API levels; exercising the branch would require Robolectric
 *   with `@Config(sdk = [30])` plus a compose rule, which adds no coverage
 *   beyond the gate + palette identity already asserted here.
 */
class ThemePaletteTest {

    // ── resolveThemeColors: mode → palette mapping ─────────────────────────

    @Test
    fun resolveThemeColors_systemMode_darkSelectsDarkPalette() {
        assertPaletteEquals(darkPalette(), resolveThemeColors(ThemeMode.SYSTEM, isDark = true))
    }

    @Test
    fun resolveThemeColors_systemMode_lightSelectsLightPalette() {
        assertPaletteEquals(lightPalette(), resolveThemeColors(ThemeMode.SYSTEM, isDark = false))
    }

    @Test
    fun resolveThemeColors_fixedModes_ignoreIsDark() {
        for (isDark in listOf(true, false)) {
            assertPaletteEquals(lightPalette(), resolveThemeColors(ThemeMode.LIGHT, isDark))
            assertPaletteEquals(darkPalette(), resolveThemeColors(ThemeMode.DARK, isDark))
            assertPaletteEquals(oledPalette(), resolveThemeColors(ThemeMode.OLED, isDark))
        }
    }

    @Test
    fun resolveThemeColors_highContrast_followsIsDark() {
        assertPaletteEquals(highContrastDarkPalette(), resolveThemeColors(ThemeMode.HIGH_CONTRAST, isDark = true))
        assertPaletteEquals(highContrastLightPalette(), resolveThemeColors(ThemeMode.HIGH_CONTRAST, isDark = false))
    }

    @Test
    fun resolveThemeColors_dynamicModes_returnFixedFallback() {
        // The pure resolver cannot extract wallpaper colors (no composition
        // context); AppTheme intercepts DYNAMIC_* and calls the composable
        // extractors. Here the fallback contract is pinned.
        assertPaletteEquals(lightPalette(), resolveThemeColors(ThemeMode.DYNAMIC_LIGHT, isDark = true))
        assertPaletteEquals(darkPalette(), resolveThemeColors(ThemeMode.DYNAMIC_DARK, isDark = false))
    }

    // ── supportsDynamicColors: SDK gate boundary (ADR-0004) ────────────────

    @Test
    fun supportsDynamicColors_belowApi31_returnsFalse() {
        assertFalse(supportsDynamicColors(apiLevel = 26))
        assertFalse(supportsDynamicColors(apiLevel = 30))
    }

    @Test
    fun supportsDynamicColors_api31AndAbove_returnsTrue() {
        assertTrue(supportsDynamicColors(apiLevel = 31))
        assertTrue(supportsDynamicColors(apiLevel = 35))
    }

    // ── Sanity: palettes are genuinely distinct ────────────────────────────

    @Test
    fun lightPalette_and_darkPalette_areDistinct() {
        assertNotEquals(lightPalette().extendedColors, darkPalette().extendedColors)
        assertNotEquals(lightPalette().colorScheme.primary, darkPalette().colorScheme.primary)
        assertNotEquals(lightPalette().colorScheme.background, darkPalette().colorScheme.background)
    }

    private fun assertPaletteEquals(expected: ThemeColors, actual: ThemeColors) {
        assertEquals("extendedColors", expected.extendedColors, actual.extendedColors)
        val expectedScheme = expected.colorScheme
        val actualScheme = actual.colorScheme
        // Representative slots that uniquely identify each palette
        assertEquals("primary", expectedScheme.primary, actualScheme.primary)
        assertEquals("onPrimary", expectedScheme.onPrimary, actualScheme.onPrimary)
        assertEquals("background", expectedScheme.background, actualScheme.background)
        assertEquals("surface", expectedScheme.surface, actualScheme.surface)
        assertEquals("surfaceVariant", expectedScheme.surfaceVariant, actualScheme.surfaceVariant)
        assertEquals("error", expectedScheme.error, actualScheme.error)
        assertEquals("outline", expectedScheme.outline, actualScheme.outline)
        assertEquals("surfaceContainer", expectedScheme.surfaceContainer, actualScheme.surfaceContainer)
    }
}
