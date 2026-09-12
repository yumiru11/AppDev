package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
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

    // ── ExtendedColors: plan.md §5.3 GitHub state fields（审计 P1）──────────

    @Test
    fun extendedColors_everyPalette_specStateFieldsAreSpecified() {
        // 审计 P1：spec 的 8 个状态字段此前完全缺失（实现只做了 GitHub Alert 卡片语义）。
        // 每个色板都必须给出真实色值，不允许 Color.Unspecified 占位。
        stateFields().forEach { (field, value) ->
            everyExtendedPalette().forEach { (palette, extended) ->
                assertNotEquals("$palette.$field must carry a real color", Color.Unspecified, value(extended))
            }
        }
    }

    @Test
    fun extendedColors_lightAndDark_everyStateFieldCarriesBothValues() {
        // 「光/暗两套值都要有」：每个字段在亮暗之间必须是两个不同的色值
        val light = lightPalette().extendedColors
        val dark = darkPalette().extendedColors
        stateFields().forEach { (field, value) ->
            assertNotEquals("$field must differ between light and dark", value(light), value(dark))
        }
    }

    @Test
    fun extendedColors_everyPalette_stateFieldsFollowPlanMapping() {
        // 可追溯性：字段值不是凭空取的，而是 plan.md §5.3 的映射
        // （open→info/primary 蓝、merged→tertiary 紫、draft→surfaceContainerHigh+onSurfaceVariant、
        //   checks pending→warning，与 alert warning 同源）
        everyThemePalette().forEach { (palette, theme) ->
            val scheme = theme.colorScheme
            val extended = theme.extendedColors
            assertEquals("$palette.info", scheme.primary, extended.info)
            assertEquals("$palette.onInfo", scheme.onPrimary, extended.onInfo)
            assertEquals("$palette.merged", scheme.tertiary, extended.merged)
            assertEquals("$palette.onMerged", scheme.onTertiary, extended.onMerged)
            assertEquals("$palette.draft", scheme.surfaceContainerHigh, extended.draft)
            assertEquals("$palette.onDraft", scheme.onSurfaceVariant, extended.onDraft)
            assertEquals("$palette.warning", extended.onWarningContainer, extended.warning)
        }
    }

    @Test
    fun extendedColorsFrom_lightAndDarkSchemes_stateFieldsAreDerivedAndDistinct() {
        // 动态色 / seed 色的兜底派生（rememberExtendedColors 的纯函数内核）也必须给全 8 个字段
        val lightScheme = lightColorScheme()
        val darkScheme = darkColorScheme()
        val light = extendedColorsFrom(lightScheme)
        val dark = extendedColorsFrom(darkScheme)
        stateFields().forEach { (field, value) ->
            assertNotEquals("$field must be derived (light)", Color.Unspecified, value(light))
            assertNotEquals("$field must be derived (dark)", Color.Unspecified, value(dark))
            assertNotEquals("$field must differ between light and dark schemes", value(light), value(dark))
        }
        assertEquals("info ← primary", lightScheme.primary, light.info)
        assertEquals("merged ← tertiary", lightScheme.tertiary, light.merged)
        assertEquals("warning ← tertiary (yellow-approx)", lightScheme.tertiary, light.warning)
        assertEquals("draft ← surfaceContainerHigh", lightScheme.surfaceContainerHigh, light.draft)
        assertEquals("onDraft ← onSurfaceVariant", lightScheme.onSurfaceVariant, light.onDraft)
    }

    /** plan.md §5.3 的 8 个状态字段（字段名 → 取值器），用于遍历式断言。 */
    private fun stateFields(): List<Pair<String, (ExtendedColors) -> Color>> =
        listOf(
            "warning" to { it.warning },
            "onWarning" to { it.onWarning },
            "info" to { it.info },
            "onInfo" to { it.onInfo },
            "merged" to { it.merged },
            "onMerged" to { it.onMerged },
            "draft" to { it.draft },
            "onDraft" to { it.onDraft },
        )

    private fun everyThemePalette(): Map<String, ThemeColors> =
        mapOf(
            "light" to lightPalette(),
            "dark" to darkPalette(),
            "oled" to oledPalette(),
            "highContrastLight" to highContrastLightPalette(),
            "highContrastDark" to highContrastDarkPalette(),
        )

    private fun everyExtendedPalette(): Map<String, ExtendedColors> =
        everyThemePalette()
            .mapValues { (_, theme) -> theme.extendedColors }
            .plus("default" to DefaultExtendedColors)

    private fun assertPaletteEquals(
        expected: ThemeColors,
        actual: ThemeColors,
    ) {
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
