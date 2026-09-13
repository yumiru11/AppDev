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

    /**
     * 间距 scale（设计系统 Batch 3，`implementation-plan.md` §2 决策 7）：
     * xs=4 / s=8 / m=12 / l=16 / xl=24 / xxl=32。
     *
     * 全应用结构间距（padding / gap / spacer）一律优先消费本 scale；新代码不再写裸
     * 间距字面量。既有调用点可继续用 [contentPadding] 等旧名——它们是本 scale 的别名。
     *
     * 用法：`Modifier.padding(AppDimens.spacing.l)`、
     * `Arrangement.spacedBy(AppDimens.spacing.s)`。
     */
    val spacing: SpacingScale = SpacingScale()

    /**
     * 内容通行内边距（16dp）——旧名别名，等价 `spacing.l`（`implementation-plan.md` §9）：
     * 语义与数值均不变，既有调用点零改动。
     */
    val contentPadding: Dp = spacing.l

    /** 最小触点（M3 无障碍基线；#87 SeedColorRow 色块等小控件的触区下限） */
    val minTouchTarget: Dp = 48.dp

    /**
     * FAB 下方内容避让（UI-5）：ExtendedFloatingActionButton（56dp 高）悬浮在内容右下角，
     * 滚动容器底部须预留该留白，保证末条内容可滚到 FAB 之上、不被压住。
     * 口径 = FAB 高 56dp + 底边距 16dp + 呼吸位 24dp = 96dp（M3 FAB 避让基线）。
     */
    val fabContentClearance: Dp = 96.dp
}
