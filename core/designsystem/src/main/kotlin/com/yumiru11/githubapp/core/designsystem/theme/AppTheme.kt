package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.token.LocalMotionScale

/**
 * Material 3 theme wrapper with extended semantic colors and shape tokens.
 *
 * Resolves a [ThemeMode] into concrete [ThemeColors] and provides them via
 * [MaterialTheme] and [ExtendedColorsProvider]. Existing callers that only
 * pass `darkTheme` are unaffected — the new [themeMode] parameter defaults
 * to [ThemeMode.SYSTEM].
 *
 * Shapes are explicitly injected from [AppDimens] × [cornerScale]
 * (ui-design.md §1.1-5 "圆角全覆盖"; settings "圆角强度" slider). At the
 * default scale of 1f they equal the M3 defaults, so callers that don't pass
 * it see unchanged visuals.
 *
 * [motionScale] is provided via [LocalMotionScale] (ui-design §4.4): callers
 * pass `min(DataStore motionScale, system animator scale)` so every
 * [AppMotion.scaledDuration] consumer honours the system "remove animations"
 * setting. Default 1f = no scaling.
 *
 * Usage:
 * ```
 * // Backward-compatible (T3 callers — no change needed):
 * AppTheme { MyScreen() }
 *
 * // Explicit theme mode:
 * AppTheme(themeMode = ThemeMode.OLED) { MyScreen() }
 *
 * // Settings-driven scales (AppThemeHost):
 * AppTheme(cornerScale = 1.2f, motionScale = 0.8f) { MyScreen() }
 * ```
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color? = null,
    cornerScale: Float = 1f,
    motionScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.SYSTEM -> darkTheme

            ThemeMode.LIGHT -> false

            ThemeMode.DARK,
            ThemeMode.OLED,
            ThemeMode.DYNAMIC_DARK,
            -> true

            ThemeMode.DYNAMIC_LIGHT -> false

            ThemeMode.HIGH_CONTRAST -> darkTheme // follows system
        }

    val themeColors =
        when (themeMode) {
            // Dynamic modes need a composition context (wallpaper extraction on API 31+);
            // the pure resolver returns fixed fallbacks for the other 5 modes.
            ThemeMode.DYNAMIC_LIGHT -> dynamicLightColors(LocalContext.current)

            ThemeMode.DYNAMIC_DARK -> dynamicDarkColors(LocalContext.current)

            // OLED / HIGH_CONTRAST keep their fixed palettes; seed only tints the
            // base SYSTEM/LIGHT/DARK modes (T24 settings "seed 色盘").
            ThemeMode.OLED,
            ThemeMode.HIGH_CONTRAST,
            -> resolveThemeColors(themeMode, isDark)

            else -> seedColor?.let { seedColorScheme(it, isDark) } ?: resolveThemeColors(themeMode, isDark)
        }

    CompositionLocalProvider(
        ExtendedColorsProvider.Local provides themeColors.extendedColors,
        LocalMotionScale provides motionScale,
    ) {
        MaterialTheme(
            colorScheme = themeColors.colorScheme,
            shapes = AppShapes.from(cornerScale),
            content = content,
        )
    }
}
