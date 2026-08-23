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
    val cornerExtraSmall: Dp = 4.dp
    val cornerSmall: Dp = 8.dp
    val cornerMedium: Dp = 12.dp
    val cornerLarge: Dp = 16.dp

    /** 分组卡外圆角（#87：CardGroup 分段卡首尾条目的外缘） */
    val cornerCardOuter: Dp = 20.dp

    /** 分组卡内条目圆角（#87：CardGroup 中段条目的常态圆角） */
    val cornerCardInner: Dp = 4.dp

    val cornerExtraLarge: Dp = 28.dp

    // Spacing
    val contentPadding: Dp = 16.dp

    /** 最小触点（M3 无障碍基线；#87 SeedColorRow 色块等小控件的触区下限） */
    val minTouchTarget: Dp = 48.dp
}
