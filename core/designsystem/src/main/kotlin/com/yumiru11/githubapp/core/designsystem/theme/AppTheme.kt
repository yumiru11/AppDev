package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.yumiru11.githubapp.core.datastore.model.ThemeMode

/**
 * Material 3 theme wrapper with extended semantic colors.
 *
 * Resolves a [ThemeMode] into concrete [ThemeColors] and provides them via
 * [MaterialTheme] and [ExtendedColorsProvider]. Existing callers that only
 * pass `darkTheme` are unaffected — the new [themeMode] parameter defaults
 * to [ThemeMode.SYSTEM].
 *
 * Usage:
 * ```
 * // Backward-compatible (T3 callers — no change needed):
 * AppTheme { MyScreen() }
 *
 * // Explicit theme mode:
 * AppTheme(themeMode = ThemeMode.OLED) { MyScreen() }
 * ```
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK,
        ThemeMode.OLED,
        ThemeMode.DYNAMIC_DARK -> true
        ThemeMode.DYNAMIC_LIGHT -> false
        ThemeMode.HIGH_CONTRAST -> darkTheme // follows system
    }

    val themeColors = when (themeMode) {
        // Dynamic modes need a composition context (wallpaper extraction on API 31+);
        // the pure resolver returns fixed fallbacks for the other 5 modes.
        ThemeMode.DYNAMIC_LIGHT -> dynamicLightColors(LocalContext.current)
        ThemeMode.DYNAMIC_DARK -> dynamicDarkColors(LocalContext.current)
        else -> resolveThemeColors(themeMode, isDark)
    }

    CompositionLocalProvider(
        ExtendedColorsProvider.Local provides themeColors.extendedColors,
    ) {
        MaterialTheme(
            colorScheme = themeColors.colorScheme,
            content = content,
        )
    }
}
