package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color extensions beyond Material 3's [ColorScheme].
 *
 * These tokens serve domain-specific roles (GitHub alert cards, brand identity,
 * success/danger semantics, and the GitHub state colors of plan.md §5.3 —
 * success/warning/info/merged/draft) that T7/T26 will consume. Components MUST
 * reference these tokens — never hardcode color values.
 *
 * Color origin labels (per field):
 * - **Material-derived** — mapped from Material 3 color roles
 * - **Brand** — GitHub brand palette (primer/primitives)
 * - **Semantic** — domain-meaningful colors with no direct Material role
 */
@Immutable
data class ExtendedColors(
    /** GitHub alert note container background. Material-derived (primaryContainer). */
    val noteContainer: Color,
    /** Text/icon on noteContainer. Material-derived (onPrimaryContainer). */
    val onNoteContainer: Color,
    /** GitHub alert tip container background. Semantic (success family). */
    val tipContainer: Color,
    /** Text/icon on tipContainer. Semantic (success family). */
    val onTipContainer: Color,
    /** GitHub alert important container background. Material-derived (tertiaryContainer). */
    val importantContainer: Color,
    /** Text/icon on importantContainer. Material-derived (onTertiaryContainer). */
    val onImportantContainer: Color,
    /** GitHub alert warning container background. Semantic (attention family). */
    val warningContainer: Color,
    /** Text/icon on warningContainer. Semantic (attention family). */
    val onWarningContainer: Color,
    /** GitHub alert caution container background. Material-derived (errorContainer). */
    val cautionContainer: Color,
    /** Text/icon on cautionContainer. Material-derived (onErrorContainer). */
    val onCautionContainer: Color,
    /** GitHub brand primary blue. Brand (primer/primitives). */
    val brand: Color,
    /** Success action color. Semantic. */
    val success: Color,
    /** Success container background. Semantic. */
    val successContainer: Color,
    /** Text/icon on success button. Semantic. */
    val onSuccess: Color,
    /** Text/icon on successContainer. Semantic. */
    val onSuccessContainer: Color,
    /** Danger action color. Semantic. */
    val danger: Color,
    /** Danger container background. Semantic. */
    val dangerContainer: Color,
    /** Text/icon on danger button. Semantic. */
    val onDanger: Color,
    /** Text/icon on dangerContainer. Semantic. */
    val onDangerContainer: Color,
    /**
     * Attention accent — "Checks pending" (plan.md §5.3: pending → warning).
     * GitHub attention foreground; same source as [onWarningContainer], so alert
     * cards and check-run states share one amber. Semantic.
     *
     * First of the §5.3 state fields that the 2026-09-11 audit found missing
     * (8 in total); every palette (light/dark/OLED/high-contrast×2) sets all of
     * them, pinned to the matching Material roles by ThemePaletteTest so the values
     * stay traceable to plan.md §5.3 rather than invented here.
     */
    val warning: Color,
    /** Text/icon on a solid [warning] surface. Semantic. */
    val onWarning: Color,
    /**
     * Informational accent — blue, GitHub's `accent` family and the
     * "Open / reopened" state colour (plan.md §5.3). Semantic (info-blue).
     */
    val info: Color,
    /** Text/icon on a solid [info] surface. Semantic. */
    val onInfo: Color,
    /** Merged-PR accent (plan.md §5.3: merged → tertiary, the purple family). Semantic. */
    val merged: Color,
    /** Text/icon on a solid [merged] surface. Semantic. */
    val onMerged: Color,
    /** Draft-PR chip background (plan.md §5.3: draft → surfaceContainerHigh). Material-derived. */
    val draft: Color,
    /** Text/icon on [draft] (plan.md §5.3: draft → onSurfaceVariant). Material-derived. */
    val onDraft: Color,
)

/** Default extended colors for preview / testing. */
@Stable
val DefaultExtendedColors =
    ExtendedColors(
        noteContainer = Color(0xFFDDF4FF),
        onNoteContainer = Color(0xFF0550AE),
        tipContainer = Color(0xFFDAFBE1),
        onTipContainer = Color(0xFF1A7F37),
        importantContainer = Color(0xFFFBEFFF),
        onImportantContainer = Color(0xFF8250DF),
        warningContainer = Color(0xFFFFF8C5),
        onWarningContainer = Color(0xFF9A6700),
        cautionContainer = Color(0xFFFFEBE9),
        onCautionContainer = Color(0xFFCF222E),
        brand = Color(0xFF0969DA),
        success = Color(0xFF1A7F37),
        successContainer = Color(0xFFDAFBE1),
        onSuccess = Color(0xFFFFFFFF),
        onSuccessContainer = Color(0xFF1A7F37),
        danger = Color(0xFFCF222E),
        dangerContainer = Color(0xFFFFEBE9),
        onDanger = Color(0xFFFFFFFF),
        onDangerContainer = Color(0xFFCF222E),
        // plan.md §5.3 state colors — mirrors lightPalette so previews/tests match LIGHT
        warning = Color(0xFF9A6700),
        onWarning = Color(0xFFFFFFFF),
        info = Color(0xFF0969DA),
        onInfo = Color(0xFFFFFFFF),
        merged = Color(0xFF8250DF),
        onMerged = Color(0xFFFFFFFF),
        draft = Color(0xFFE6E9ED),
        onDraft = Color(0xFF656D76),
    )

private val LocalExtendedColors = staticCompositionLocalOf { DefaultExtendedColors }

/**
 * Provides [ExtendedColors] down the composition tree.
 */
internal object ExtendedColorsProvider {
    val Local = LocalExtendedColors
}

/** Access extended colors from any composable via [MaterialTheme]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = ExtendedColorsProvider.Local.current

/**
 * Derive [ExtendedColors] from a Material [ColorScheme].
 *
 * Maps Material 3 color roles to semantic roles using a best-effort heuristic.
 * Palette functions in [ThemeColors.kt] provide manually tuned values; this
 * function is a fallback for custom or dynamic color schemes.
 *
 * Composable only because callers use it inside composition; the derivation
 * itself is the pure [extendedColorsFrom] so it stays unit-testable on the JVM
 * (dynamic / seed schemes have no screenshot path).
 *
 * @param colorScheme the active Material 3 [ColorScheme]
 */
@Composable
fun rememberExtendedColors(colorScheme: ColorScheme): ExtendedColors = extendedColorsFrom(colorScheme)

/**
 * Pure role mapping behind [rememberExtendedColors] (JVM unit-testable).
 *
 * @param colorScheme the active Material 3 [ColorScheme]
 */
internal fun extendedColorsFrom(colorScheme: ColorScheme): ExtendedColors =
    ExtendedColors(
        // Alert note → primary family (info-blue)
        noteContainer = colorScheme.primaryContainer,
        onNoteContainer = colorScheme.onPrimaryContainer,
        // Alert tip → tertiary family (green)
        tipContainer = colorScheme.tertiaryContainer,
        onTipContainer = colorScheme.onTertiaryContainer,
        // Alert important → tertiary family (purple)
        importantContainer = colorScheme.tertiaryContainer,
        onImportantContainer = colorScheme.onTertiaryContainer,
        // Alert warning → tertiary (yellow-approx via tertiary)
        warningContainer = colorScheme.tertiaryContainer,
        onWarningContainer = colorScheme.onTertiaryContainer,
        // Alert caution → error family (red)
        cautionContainer = colorScheme.errorContainer,
        onCautionContainer = colorScheme.onErrorContainer,
        // Brand → primary
        brand = colorScheme.primary,
        // Success → tertiary
        success = colorScheme.tertiary,
        successContainer = colorScheme.tertiaryContainer,
        onSuccess = colorScheme.onTertiary,
        onSuccessContainer = colorScheme.onTertiaryContainer,
        // Danger → error
        danger = colorScheme.error,
        dangerContainer = colorScheme.errorContainer,
        onDanger = colorScheme.onError,
        onDangerContainer = colorScheme.onErrorContainer,
        // State colors (plan.md §5.3) — dynamic/seed schemes have no hand-tuned
        // amber/gray, so warning borrows the tertiary family it already uses for
        // warningContainer, info follows the note blue, merged is tertiary and
        // draft is the neutral surface pair.
        warning = colorScheme.tertiary,
        onWarning = colorScheme.onTertiary,
        info = colorScheme.primary,
        onInfo = colorScheme.onPrimary,
        merged = colorScheme.tertiary,
        onMerged = colorScheme.onTertiary,
        draft = colorScheme.surfaceContainerHigh,
        onDraft = colorScheme.onSurfaceVariant,
    )
