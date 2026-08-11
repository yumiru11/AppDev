package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Minimal Material 3 theme wrapper.
 *
 * This is a scaffold for the full theme engine (ticket T6). It only wires the
 * system color scheme (light/dark) into [MaterialTheme] — no custom colors,
 * no dynamic color, no custom [androidx.compose.material3.ColorScheme].
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
