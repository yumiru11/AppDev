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
 * success/danger semantics) that T7/T26 will consume. Components MUST reference
 * these tokens — never hardcode color values.
 *
 * Color origin labels (per field):
 * - **Material-derived** — mapped from Material 3 color roles
 * - **Brand** — GitHub brand palette (primer/primitives)
 * - **Semantic** — domain-meaningful colors with no direct Material role
 */
@Immutable
data class ExtendedColors(
    // ── GitHub Alert: Note (info) ──────────────────────────────────────
    /** GitHub alert note container background. Material-derived (primaryContainer). */
    val noteContainer: Color,
    /** Text/icon on noteContainer. Material-derived (onPrimaryContainer). */
    val onNoteContainer: Color,

    // ── GitHub Alert: Tip (hint) ───────────────────────────────────────
    /** GitHub alert tip container background. Semantic (success family). */
    val tipContainer: Color,
    /** Text/icon on tipContainer. Semantic (success family). */
    val onTipContainer: Color,

    // ── GitHub Alert: Important (emphasis) ─────────────────────────────
    /** GitHub alert important container background. Material-derived (tertiaryContainer). */
    val importantContainer: Color,
    /** Text/icon on importantContainer. Material-derived (onTertiaryContainer). */
    val onImportantContainer: Color,

    // ── GitHub Alert: Warning ──────────────────────────────────────────
    /** GitHub alert warning container background. Semantic (attention family). */
    val warningContainer: Color,
    /** Text/icon on warningContainer. Semantic (attention family). */
    val onWarningContainer: Color,

    // ── GitHub Alert: Caution (danger) ─────────────────────────────────
    /** GitHub alert caution container background. Material-derived (errorContainer). */
    val cautionContainer: Color,
    /** Text/icon on cautionContainer. Material-derived (onErrorContainer). */
    val onCautionContainer: Color,

    // ── Brand ──────────────────────────────────────────────────────────
    /** GitHub brand primary blue. Brand (primer/primitives). */
    val brand: Color,

    // ── Success semantic ───────────────────────────────────────────────
    /** Success action color. Semantic. */
    val success: Color,
    /** Success container background. Semantic. */
    val successContainer: Color,
    /** Text/icon on success button. Semantic. */
    val onSuccess: Color,
    /** Text/icon on successContainer. Semantic. */
    val onSuccessContainer: Color,

    // ── Danger semantic ────────────────────────────────────────────────
    /** Danger action color. Semantic. */
    val danger: Color,
    /** Danger container background. Semantic. */
    val dangerContainer: Color,
    /** Text/icon on danger button. Semantic. */
    val onDanger: Color,
    /** Text/icon on dangerContainer. Semantic. */
    val onDangerContainer: Color,
)

/** Default extended colors for preview / testing. */
@Stable
val DefaultExtendedColors = ExtendedColors(
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
)

// ── CompositionLocal ───────────────────────────────────────────────────────

private val LocalExtendedColors = staticCompositionLocalOf { DefaultExtendedColors }

/**
 * Provides [ExtendedColors] down the composition tree.
 */
internal object ExtendedColorsProvider {
    val Local = LocalExtendedColors
}

// ── Extension property ─────────────────────────────────────────────────────

/** Access extended colors from any composable via [MaterialTheme]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = ExtendedColorsProvider.Local.current

/**
 * Derive [ExtendedColors] from a Material [ColorScheme].
 *
 * Maps Material 3 color roles to semantic roles using a best-effort heuristic.
 * Palette functions in [Palette.kt] provide manually tuned values; this
 * function is a fallback for custom or dynamic color schemes.
 *
 * @param colorScheme the active Material 3 [ColorScheme]
 */
@Composable
fun rememberExtendedColors(colorScheme: ColorScheme): ExtendedColors =
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
    )
