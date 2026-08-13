package com.yumiru11.githubapp.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yumiru11.githubapp.core.datastore.model.ThemeMode

// ── ThemeColors bundle ─────────────────────────────────────────────────────

/**
 * Bundles a Material 3 [ColorScheme] with [ExtendedColors] for a single theme.
 */
data class ThemeColors(
    val colorScheme: ColorScheme,
    val extendedColors: ExtendedColors,
)

// ══════════════════════════════════════════════════════════════════════════════
// Light palette
// ══════════════════════════════════════════════════════════════════════════════

/** GitHub Light — Material 3 color roles using primer/primitives values. */
fun lightPalette(): ThemeColors =
    ThemeColors(
        colorScheme =
            lightColorScheme(
                // Primary — GitHub brand blue
                primary = Color(0xFF0969DA), // Brand
                onPrimary = Color(0xFFFFFFFF), // Brand
                primaryContainer = Color(0xFFC8E1FF), // Brand (lighter blue)
                onPrimaryContainer = Color(0xFF003366), // Brand (deep blue)
                // Secondary — neutral gray-blue
                secondary = Color(0xFF6E7781), // Material-derived
                onSecondary = Color(0xFFFFFFFF), // Material-derived
                secondaryContainer = Color(0xFFE2E3E5), // Material-derived
                onSecondaryContainer = Color(0xFF24292F), // Material-derived
                // Tertiary — GitHub purple
                tertiary = Color(0xFF8250DF), // Brand (purple)
                onTertiary = Color(0xFFFFFFFF), // Brand
                tertiaryContainer = Color(0xFFFBEFFF), // Brand (light purple)
                onTertiaryContainer = Color(0xFF3B1F6E), // Brand (deep purple)
                // Error — danger red
                error = Color(0xFFCF222E), // Semantic
                onError = Color(0xFFFFFFFF), // Semantic
                errorContainer = Color(0xFFFFEBE9), // Semantic
                onErrorContainer = Color(0xFF5C1D18), // Semantic
                // Surfaces
                background = Color(0xFFFFFFFF), // Material-derived
                onBackground = Color(0xFF24292F), // Material-derived
                surface = Color(0xFFFFFFFF), // Material-derived
                onSurface = Color(0xFF24292F), // Material-derived
                surfaceVariant = Color(0xFFF6F8FA), // Material-derived
                onSurfaceVariant = Color(0xFF656D76), // Material-derived
                surfaceTint = Color(0xFF0969DA), // Brand (tint = primary)
                outline = Color(0xFFD0D7DE), // Material-derived
                outlineVariant = Color(0xFFE2E3E5), // Material-derived
                inverseSurface = Color(0xFF24292F), // Material-derived
                inverseOnSurface = Color(0xFFE6EDF3), // Material-derived
                inversePrimary = Color(0xFFC8E1FF), // Brand
                surfaceDim = Color(0xFFF6F8FA), // Material-derived
                surfaceBright = Color(0xFFFFFFFF), // Material-derived
                surfaceContainerLowest = Color(0xFFFFFFFF), // Material-derived
                surfaceContainerLow = Color(0xFFF6F8FA), // Material-derived
                surfaceContainer = Color(0xFFEEF1F5), // Material-derived
                surfaceContainerHigh = Color(0xFFE6E9ED), // Material-derived
                surfaceContainerHighest = Color(0xFFD0D7DE), // Material-derived
            ),
        extendedColors =
            ExtendedColors(
                noteContainer = Color(0xFFDDF4FF), // GitHub alert: note (blue)
                onNoteContainer = Color(0xFF0550AE), // GitHub alert: note
                tipContainer = Color(0xFFDAFBE1), // GitHub alert: tip (green)
                onTipContainer = Color(0xFF1A7F37), // GitHub alert: tip
                importantContainer = Color(0xFFFBEFFF), // GitHub alert: important (purple)
                onImportantContainer = Color(0xFF8250DF), // GitHub alert: important
                warningContainer = Color(0xFFFFF8C5), // GitHub alert: warning (yellow)
                onWarningContainer = Color(0xFF9A6700), // GitHub alert: warning
                cautionContainer = Color(0xFFFFEBE9), // GitHub alert: caution (red)
                onCautionContainer = Color(0xFFCF222E), // GitHub alert: caution
                brand = Color(0xFF0969DA), // Brand (primer blue)
                success = Color(0xFF1A7F37), // Semantic (green)
                successContainer = Color(0xFFDAFBE1), // Semantic
                onSuccess = Color(0xFFFFFFFF), // Semantic
                onSuccessContainer = Color(0xFF1A7F37), // Semantic
                danger = Color(0xFFCF222E), // Semantic (red)
                dangerContainer = Color(0xFFFFEBE9), // Semantic
                onDanger = Color(0xFFFFFFFF), // Semantic
                onDangerContainer = Color(0xFFCF222E), // Semantic
            ),
    )

// ══════════════════════════════════════════════════════════════════════════════
// Dark palette
// ══════════════════════════════════════════════════════════════════════════════

/** GitHub Dark — dark surface with brighter brand accents. */
fun darkPalette(): ThemeColors =
    ThemeColors(
        colorScheme =
            darkColorScheme(
                // Primary — GitHub brand blue (bright on dark)
                primary = Color(0xFF79C0FF), // Brand
                onPrimary = Color(0xFF0D2942), // Brand
                primaryContainer = Color(0xFF0D419D), // Brand (deep blue)
                onPrimaryContainer = Color(0xFFC8E1FF), // Brand
                // Secondary — muted gray
                secondary = Color(0xFF8B949E), // Material-derived
                onSecondary = Color(0xFF24292F), // Material-derived
                secondaryContainer = Color(0xFF30363D), // Material-derived
                onSecondaryContainer = Color(0xFFE2E3E5), // Material-derived
                // Tertiary — bright purple
                tertiary = Color(0xFFD2A8FF), // Brand (purple)
                onTertiary = Color(0xFF271052), // Brand
                tertiaryContainer = Color(0xFF553098), // Brand (mid purple)
                onTertiaryContainer = Color(0xFFFBEFFF), // Brand
                // Error — danger red (bright on dark)
                error = Color(0xFFFFA198), // Semantic
                onError = Color(0xFF3D0F0F), // Semantic
                errorContainer = Color(0xFF5C1D18), // Semantic
                onErrorContainer = Color(0xFFFFEBE9), // Semantic
                // Surfaces — GitHub dark (#0D1117)
                background = Color(0xFF0D1117), // Material-derived
                onBackground = Color(0xFFE6EDF3), // Material-derived
                surface = Color(0xFF0D1117), // Material-derived
                onSurface = Color(0xFFE6EDF3), // Material-derived
                surfaceVariant = Color(0xFF161B22), // Material-derived
                onSurfaceVariant = Color(0xFF8B949E), // Material-derived
                surfaceTint = Color(0xFF79C0FF), // Brand (tint = primary)
                outline = Color(0xFF30363D), // Material-derived
                outlineVariant = Color(0xFF21262D), // Material-derived
                inverseSurface = Color(0xFFE6EDF3), // Material-derived
                inverseOnSurface = Color(0xFF24292F), // Material-derived
                inversePrimary = Color(0xFF0D419D), // Brand
                surfaceDim = Color(0xFF0D1117), // Material-derived
                surfaceBright = Color(0xFF161B22), // Material-derived
                surfaceContainerLowest = Color(0xFF010409), // Material-derived
                surfaceContainerLow = Color(0xFF0D1117), // Material-derived
                surfaceContainer = Color(0xFF161B22), // Material-derived
                surfaceContainerHigh = Color(0xFF21262D), // Material-derived
                surfaceContainerHighest = Color(0xFF30363D), // Material-derived
            ),
        extendedColors =
            ExtendedColors(
                noteContainer = Color(0xFF0D2942), // GitHub alert: note (dark blue)
                onNoteContainer = Color(0xFF79C0FF), // GitHub alert: note
                tipContainer = Color(0xFF0F2D16), // GitHub alert: tip (dark green)
                onTipContainer = Color(0xFF7EE787), // GitHub alert: tip
                importantContainer = Color(0xFF271052), // GitHub alert: important (dark purple)
                onImportantContainer = Color(0xFFD2A8FF), // GitHub alert: important
                warningContainer = Color(0xFF2D1E00), // GitHub alert: warning (dark yellow)
                onWarningContainer = Color(0xFFE3B341), // GitHub alert: warning
                cautionContainer = Color(0xFF3D0F0F), // GitHub alert: caution (dark red)
                onCautionContainer = Color(0xFFFFA198), // GitHub alert: caution
                brand = Color(0xFF79C0FF), // Brand (bright blue)
                success = Color(0xFF7EE787), // Semantic (bright green)
                successContainer = Color(0xFF0F2D16), // Semantic
                onSuccess = Color(0xFF0D1117), // Semantic
                onSuccessContainer = Color(0xFF7EE787), // Semantic
                danger = Color(0xFFFFA198), // Semantic (bright red)
                dangerContainer = Color(0xFF3D0F0F), // Semantic
                onDanger = Color(0xFF0D1117), // Semantic
                onDangerContainer = Color(0xFFFFA198), // Semantic
            ),
    )

// ══════════════════════════════════════════════════════════════════════════════
// OLED palette — pure black surfaces
// ══════════════════════════════════════════════════════════════════════════════

/** OLED Dark — pure black (#000000) surfaces for power-saving on AMOLED. */
fun oledPalette(): ThemeColors =
    ThemeColors(
        colorScheme =
            darkColorScheme(
                // Primary — same bright blue as dark palette
                primary = Color(0xFF79C0FF), // Brand
                onPrimary = Color(0xFF0D2942), // Brand
                primaryContainer = Color(0xFF0D419D), // Brand
                onPrimaryContainer = Color(0xFFC8E1FF), // Brand
                // Secondary
                secondary = Color(0xFF8B949E), // Material-derived
                onSecondary = Color(0xFF24292F), // Material-derived
                secondaryContainer = Color(0xFF30363D), // Material-derived
                onSecondaryContainer = Color(0xFFE2E3E5), // Material-derived
                // Tertiary
                tertiary = Color(0xFFD2A8FF), // Brand
                onTertiary = Color(0xFF271052), // Brand
                tertiaryContainer = Color(0xFF553098), // Brand
                onTertiaryContainer = Color(0xFFFBEFFF), // Brand
                // Error
                error = Color(0xFFFFA198), // Semantic
                onError = Color(0xFF3D0F0F), // Semantic
                errorContainer = Color(0xFF5C1D18), // Semantic
                onErrorContainer = Color(0xFFFFEBE9), // Semantic
                // Surfaces — PURE BLACK
                background = Color(0xFF000000), // OLED (pure black)
                onBackground = Color(0xFFE6EDF3), // Material-derived
                surface = Color(0xFF000000), // OLED (pure black)
                onSurface = Color(0xFFE6EDF3), // Material-derived
                surfaceVariant = Color(0xFF000000), // OLED (pure black)
                onSurfaceVariant = Color(0xFF8B949E), // Material-derived
                surfaceTint = Color(0xFF79C0FF), // Brand
                outline = Color(0xFF30363D), // Material-derived
                outlineVariant = Color(0xFF21262D), // Material-derived
                inverseSurface = Color(0xFFE6EDF3), // Material-derived
                inverseOnSurface = Color(0xFF24292F), // Material-derived
                inversePrimary = Color(0xFF0D419D), // Brand
                surfaceDim = Color(0xFF000000), // OLED
                surfaceBright = Color(0xFF161B22), // Material-derived
                surfaceContainerLowest = Color(0xFF000000), // OLED
                surfaceContainerLow = Color(0xFF000000), // OLED
                surfaceContainer = Color(0xFF0D1117), // Slight elevation
                surfaceContainerHigh = Color(0xFF161B22), // Material-derived
                surfaceContainerHighest = Color(0xFF21262D), // Material-derived
            ),
        extendedColors =
            ExtendedColors(
                noteContainer = Color(0xFF0D2942), // GitHub alert: note
                onNoteContainer = Color(0xFF79C0FF), // GitHub alert: note
                tipContainer = Color(0xFF0F2D16), // GitHub alert: tip
                onTipContainer = Color(0xFF7EE787), // GitHub alert: tip
                importantContainer = Color(0xFF271052), // GitHub alert: important
                onImportantContainer = Color(0xFFD2A8FF), // GitHub alert: important
                warningContainer = Color(0xFF2D1E00), // GitHub alert: warning
                onWarningContainer = Color(0xFFE3B341), // GitHub alert: warning
                cautionContainer = Color(0xFF3D0F0F), // GitHub alert: caution
                onCautionContainer = Color(0xFFFFA198), // GitHub alert: caution
                brand = Color(0xFF79C0FF), // Brand
                success = Color(0xFF7EE787), // Semantic
                successContainer = Color(0xFF0F2D16), // Semantic
                onSuccess = Color(0xFF000000), // Semantic (pure black bg)
                onSuccessContainer = Color(0xFF7EE787), // Semantic
                danger = Color(0xFFFFA198), // Semantic
                dangerContainer = Color(0xFF3D0F0F), // Semantic
                onDanger = Color(0xFF000000), // Semantic (pure black bg)
                onDangerContainer = Color(0xFFFFA198), // Semantic
            ),
    )

// ══════════════════════════════════════════════════════════════════════════════
// Dynamic palettes — wallpaper extraction on API 31+, fixed fallback below
// ══════════════════════════════════════════════════════════════════════════════

/**
 * True when the device can provide dynamic wallpaper colors (API 31+).
 *
 * Extracted as a pure function so the SDK gate is unit-testable without
 * Robolectric; [dynamicLightColors] / [dynamicDarkColors] use it to decide
 * between real extraction and the fixed fallback palettes (ADR-0004).
 */
internal fun supportsDynamicColors(apiLevel: Int = Build.VERSION.SDK_INT): Boolean = apiLevel >= Build.VERSION_CODES.S

/**
 * Dynamic Light — wallpaper-derived Material 3 scheme on API 31+.
 *
 * Uses [dynamicLightColorScheme] (Material 3 dynamic color extraction from
 * the system wallpaper) plus [rememberExtendedColors] to derive the extended
 * semantic tokens from the dynamic scheme. Below API 31 falls back to
 * [lightPalette] with its hand-tuned GitHub values (ADR-0004).
 */
@Composable
fun dynamicLightColors(context: Context): ThemeColors {
    if (!supportsDynamicColors()) return lightPalette()
    val scheme = dynamicLightColorScheme(context)
    return ThemeColors(
        colorScheme = scheme,
        extendedColors = rememberExtendedColors(scheme),
    )
}

/**
 * Dynamic Dark — wallpaper-derived Material 3 scheme on API 31+.
 *
 * Dark counterpart of [dynamicLightColors]: uses [dynamicDarkColorScheme]
 * and [rememberExtendedColors] on API 31+, falls back to [darkPalette]
 * below (ADR-0004).
 */
@Composable
fun dynamicDarkColors(context: Context): ThemeColors {
    if (!supportsDynamicColors()) return darkPalette()
    val scheme = dynamicDarkColorScheme(context)
    return ThemeColors(
        colorScheme = scheme,
        extendedColors = rememberExtendedColors(scheme),
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// High Contrast palette
// ══════════════════════════════════════════════════════════════════════════════

/** High Contrast Light — stronger contrast ratios for accessibility. */
fun highContrastLightPalette(): ThemeColors =
    ThemeColors(
        colorScheme =
            lightColorScheme(
                // Primary — deeper blue for better contrast (4.6:1 on white)
                primary = Color(0xFF0550AE), // Brand (high-contrast)
                onPrimary = Color(0xFFFFFFFF), // Brand
                primaryContainer = Color(0xFFB6D4F5), // Brand
                onPrimaryContainer = Color(0xFF00264D), // Brand
                // Secondary — darker neutral
                secondary = Color(0xFF57606A), // Material-derived (HC)
                onSecondary = Color(0xFFFFFFFF), // Material-derived
                secondaryContainer = Color(0xFFD0D7DE), // Material-derived (HC)
                onSecondaryContainer = Color(0xFF1B1F23), // Material-derived (HC)
                // Tertiary — stronger purple
                tertiary = Color(0xFF6E40C9), // Brand (HC)
                onTertiary = Color(0xFFFFFFFF), // Brand
                tertiaryContainer = Color(0xFFF0E4FF), // Brand
                onTertiaryContainer = Color(0xFF2A1060), // Brand
                // Error — deeper red for better visibility
                error = Color(0xFFA40E26), // Semantic (HC)
                onError = Color(0xFFFFFFFF), // Semantic
                errorContainer = Color(0xFFFFD8DB), // Semantic
                onErrorContainer = Color(0xFF3B0A0E), // Semantic (HC)
                // Surfaces — pure white for maximum contrast
                background = Color(0xFFFFFFFF), // Material-derived (HC)
                onBackground = Color(0xFF000000), // Material-derived (HC)
                surface = Color(0xFFFFFFFF), // Material-derived (HC)
                onSurface = Color(0xFF000000), // Material-derived (HC)
                surfaceVariant = Color(0xFFF0F3F6), // Material-derived (HC)
                onSurfaceVariant = Color(0xFF4B5360), // Material-derived (HC)
                surfaceTint = Color(0xFF0550AE), // Brand (HC)
                outline = Color(0xFFB0B8C1), // Material-derived (HC)
                outlineVariant = Color(0xFFD0D7DE), // Material-derived
                inverseSurface = Color(0xFF1B1F23), // Material-derived (HC)
                inverseOnSurface = Color(0xFFF0F3F6), // Material-derived (HC)
                inversePrimary = Color(0xFFB6D4F5), // Brand (HC)
                surfaceDim = Color(0xFFF0F3F6), // Material-derived (HC)
                surfaceBright = Color(0xFFFFFFFF), // Material-derived
                surfaceContainerLowest = Color(0xFFFFFFFF), // Material-derived
                surfaceContainerLow = Color(0xFFF0F3F6), // Material-derived (HC)
                surfaceContainer = Color(0xFFE6EAEE), // Material-derived (HC)
                surfaceContainerHigh = Color(0xFFD0D7DE), // Material-derived (HC)
                surfaceContainerHighest = Color(0xFFB0B8C1), // Material-derived (HC)
            ),
        extendedColors =
            ExtendedColors(
                noteContainer = Color(0xFFCCE5FF), // GitHub alert: note (HC)
                onNoteContainer = Color(0xFF0550AE), // GitHub alert: note (HC)
                tipContainer = Color(0xFFC8F5D0), // GitHub alert: tip (HC)
                onTipContainer = Color(0xFF116329), // GitHub alert: tip (HC)
                importantContainer = Color(0xFFF0E4FF), // GitHub alert: important (HC)
                onImportantContainer = Color(0xFF6E40C9), // GitHub alert: important (HC)
                warningContainer = Color(0xFFFDF0C5), // GitHub alert: warning (HC)
                onWarningContainer = Color(0xFF7A4E00), // GitHub alert: warning (HC)
                cautionContainer = Color(0xFFFFD8DB), // GitHub alert: caution (HC)
                onCautionContainer = Color(0xFFA40E26), // GitHub alert: caution (HC)
                brand = Color(0xFF0550AE), // Brand (HC — deeper blue)
                success = Color(0xFF116329), // Semantic (HC — deeper green)
                successContainer = Color(0xFFC8F5D0), // Semantic (HC)
                onSuccess = Color(0xFFFFFFFF), // Semantic
                onSuccessContainer = Color(0xFF116329), // Semantic (HC)
                danger = Color(0xFFA40E26), // Semantic (HC — deeper red)
                dangerContainer = Color(0xFFFFD8DB), // Semantic (HC)
                onDanger = Color(0xFFFFFFFF), // Semantic
                onDangerContainer = Color(0xFFA40E26), // Semantic (HC)
            ),
    )

/** High Contrast Dark — brighter accents on dark background for accessibility. */
fun highContrastDarkPalette(): ThemeColors =
    ThemeColors(
        colorScheme =
            darkColorScheme(
                // Primary — brighter blue for contrast on dark
                primary = Color(0xFF58A6FF), // Brand (HC)
                onPrimary = Color(0xFF00264D), // Brand (HC)
                primaryContainer = Color(0xFF0D419D), // Brand
                onPrimaryContainer = Color(0xFFC8E1FF), // Brand
                // Secondary — brighter neutral
                secondary = Color(0xFFADBAC7), // Material-derived (HC)
                onSecondary = Color(0xFF1B1F23), // Material-derived (HC)
                secondaryContainer = Color(0xFF373E47), // Material-derived (HC)
                onSecondaryContainer = Color(0xFFF0F3F6), // Material-derived (HC)
                // Tertiary — brighter purple
                tertiary = Color(0xFFBC8CFF), // Brand (HC)
                onTertiary = Color(0xFF2A1060), // Brand (HC)
                tertiaryContainer = Color(0xFF6E40C9), // Brand (HC)
                onTertiaryContainer = Color(0xFFF0E4FF), // Brand
                // Error — brighter red
                error = Color(0xFFFF7B72), // Semantic (HC)
                onError = Color(0xFF3B0A0E), // Semantic (HC)
                errorContainer = Color(0xFFA40E26), // Semantic (HC)
                onErrorContainer = Color(0xFFFFD8DB), // Semantic (HC)
                // Surfaces
                background = Color(0xFF010409), // Material-derived (HC)
                onBackground = Color(0xFFF0F3F6), // Material-derived (HC)
                surface = Color(0xFF010409), // Material-derived (HC)
                onSurface = Color(0xFFF0F3F6), // Material-derived (HC)
                surfaceVariant = Color(0xFF161B22), // Material-derived
                onSurfaceVariant = Color(0xFFADBAC7), // Material-derived (HC)
                surfaceTint = Color(0xFF58A6FF), // Brand (HC)
                outline = Color(0xFF444C56), // Material-derived (HC)
                outlineVariant = Color(0xFF30363D), // Material-derived
                inverseSurface = Color(0xFFF0F3F6), // Material-derived (HC)
                inverseOnSurface = Color(0xFF1B1F23), // Material-derived (HC)
                inversePrimary = Color(0xFF0D419D), // Brand
                surfaceDim = Color(0xFF010409), // Material-derived (HC)
                surfaceBright = Color(0xFF161B22), // Material-derived
                surfaceContainerLowest = Color(0xFF010409), // Material-derived (HC)
                surfaceContainerLow = Color(0xFF0D1117), // Material-derived
                surfaceContainer = Color(0xFF161B22), // Material-derived
                surfaceContainerHigh = Color(0xFF21262D), // Material-derived
                surfaceContainerHighest = Color(0xFF373E47), // Material-derived (HC)
            ),
        extendedColors =
            ExtendedColors(
                noteContainer = Color(0xFF0D3A66), // GitHub alert: note (HC)
                onNoteContainer = Color(0xFF58A6FF), // GitHub alert: note (HC)
                tipContainer = Color(0xFF0D3D20), // GitHub alert: tip (HC)
                onTipContainer = Color(0xFF7EE787), // GitHub alert: tip
                importantContainer = Color(0xFF3D1F7A), // GitHub alert: important (HC)
                onImportantContainer = Color(0xFFBC8CFF), // GitHub alert: important (HC)
                warningContainer = Color(0xFF3D2E00), // GitHub alert: warning (HC)
                onWarningContainer = Color(0xFFFFD874), // GitHub alert: warning (HC)
                cautionContainer = Color(0xFF5C1D18), // GitHub alert: caution (HC)
                onCautionContainer = Color(0xFFFF7B72), // GitHub alert: caution (HC)
                brand = Color(0xFF58A6FF), // Brand (HC — brighter blue)
                success = Color(0xFF7EE787), // Semantic
                successContainer = Color(0xFF0D3D20), // Semantic (HC)
                onSuccess = Color(0xFF010409), // Semantic
                onSuccessContainer = Color(0xFF7EE787), // Semantic
                danger = Color(0xFFFF7B72), // Semantic (HC — brighter red)
                dangerContainer = Color(0xFF5C1D18), // Semantic (HC)
                onDanger = Color(0xFF010409), // Semantic
                onDangerContainer = Color(0xFFFF7B72), // Semantic
            ),
    )

// ══════════════════════════════════════════════════════════════════════════════
// Seed-color scheme（T24 设置页「seed 色盘」）
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 由单个 seed 色派生 Material 3 色板（T24 设置页「seed 色盘」）。
 *
 * 仅 primary 取 seed，其余角色用 M3 基线值；扩展色按色板角色映射
 * （[rememberExtendedColors]）。OLED / HIGH_CONTRAST / DYNAMIC 模式保留各自
 * 固定色板——seed 只作用于基础 SYSTEM/LIGHT/DARK 模式（AppTheme 内判断）。
 */
@Composable
fun seedColorScheme(
    seed: Color,
    isDark: Boolean,
): ThemeColors {
    val scheme = if (isDark) darkColorScheme(primary = seed) else lightColorScheme(primary = seed)
    return ThemeColors(colorScheme = scheme, extendedColors = rememberExtendedColors(scheme))
}

// ══════════════════════════════════════════════════════════════════════════════
// Resolver
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Resolve a [ThemeMode] into concrete [ThemeColors].
 *
 * For SYSTEM mode, [isDark] (typically from `isSystemInDarkTheme()`) decides
 * light vs dark. HIGH_CONTRAST also respects [isDark] to offer both light
 * and dark high-contrast variants.
 *
 * DYNAMIC_LIGHT / DYNAMIC_DARK return the fixed fallback palettes here so the
 * resolver stays a pure function (unit-testable without composition) and total
 * over all 7 modes. [AppTheme] intercepts those two modes and calls the real
 * composable extractors [dynamicLightColors] / [dynamicDarkColors] instead.
 */
fun resolveThemeColors(
    themeMode: ThemeMode,
    isDark: Boolean,
): ThemeColors =
    when (themeMode) {
        ThemeMode.SYSTEM -> if (isDark) darkPalette() else lightPalette()
        ThemeMode.LIGHT -> lightPalette()
        ThemeMode.DARK -> darkPalette()
        ThemeMode.OLED -> oledPalette()
        ThemeMode.DYNAMIC_LIGHT -> lightPalette()
        ThemeMode.DYNAMIC_DARK -> darkPalette()
        ThemeMode.HIGH_CONTRAST -> if (isDark) highContrastDarkPalette() else highContrastLightPalette()
    }
