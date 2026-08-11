package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scaffold design tokens — dimensions, corner radii, and spacing only.
 *
 * Values sourced from docs/ui-design.md §4/§6. No colors, no strings.
 * The full token engine (colors, typography, dynamic color) lands in ticket T6.
 */
object AppDimens {
    // Corner radii
    val cornerSmall: Dp = 8.dp
    val cornerMedium: Dp = 12.dp
    val cornerLarge: Dp = 16.dp
    val cornerExtraLarge: Dp = 28.dp

    // Spacing
    val contentPadding: Dp = 16.dp
}
