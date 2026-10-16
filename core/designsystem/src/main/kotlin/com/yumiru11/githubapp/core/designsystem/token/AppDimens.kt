package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scaffold design tokens — dimensions, corner radii, and spacing only.
 *
 * Values sourced from docs/ui-design.md §4/§6. No colors, no strings.
 *
 * 圆角族在 2026-10 的 UI 系统重构中整体上调（更圆润的容器语言）：
 * 4/8/12/16/28 → 8/12/20/28/36，分组卡外/内圆角与细缝同步放大。
 * [com.yumiru11.githubapp.core.designsystem.theme.AppShapes] 仍以本对象为唯一事实来源，
 * 设置页「圆角强度」（cornerScale 0.5–1.5）在上述基准上等比缩放。
 */
object AppDimens {
    // Corner radii
    val cornerExtraSmall: Dp = 8.dp
    val cornerSmall: Dp = 12.dp
    val cornerMedium: Dp = 20.dp
    val cornerLarge: Dp = 28.dp

    /** 分组卡外圆角（#87：CardGroup 分段卡首尾条目的外缘） */
    val cornerCardOuter: Dp = 28.dp

    /** 分组卡内条目圆角（#87：CardGroup 中段条目的常态圆角） */
    val cornerCardInner: Dp = 8.dp

    /**
     * 分组卡内条目之间的「细缝」。
     *
     * 语义：同组条目要看起来属于**同一张卡**——靠这条极细的缝分隔，而不是各自成卡。
     * 缝的颜色 = 页面底色（条目圆角外自然露底），不另画分割线。
     */
    val groupSeam: Dp = 3.dp

    val cornerExtraLarge: Dp = 36.dp

    // Spacing

    /**
     * 间距 scale（设计系统 Batch 3，`implementation-plan.md` §2 决策 7）：
     * xs=4 / s=8 / m=12 / l=16 / xl=24 / xxl=32。
     *
     * 全应用结构间距（padding / gap / spacer）一律优先消费本 scale；新代码不再写裸
     * 间距字面量。既有调用点可继续用 [contentPadding] 等旧名——它们是本 scale 的别名。
     */
    val spacing: SpacingScale = SpacingScale()

    /**
     * 内容通行内边距（16dp）——旧名别名，等价 `spacing.l`（`implementation-plan.md` §9）：
     * 语义与数值均不变，既有调用点零改动。
     */
    val contentPadding: Dp = spacing.l

    /** 最小触点（M3 无障碍基线；#87 SeedColorRow 色块等小控件的触区下限） */
    val minTouchTarget: Dp = 48.dp

    /** 主操作按钮高度（胶囊按钮的基准高度；设置页/表单页的主按钮统一取此值） */
    val buttonHeight: Dp = 56.dp

    /** 紧凑按钮高度（对话框确认、次级动作、列表内联按钮） */
    val buttonHeightCompact: Dp = 48.dp

    /**
     * FAB 下方内容避让（UI-5）：ExtendedFloatingActionButton（56dp 高）悬浮在内容右下角，
     * 滚动容器底部须预留该留白，保证末条内容可滚到 FAB 之上、不被压住。
     * 口径 = FAB 高 56dp + 底边距 16dp + 呼吸位 24dp = 96dp（M3 FAB 避让基线）。
     */
    val fabContentClearance: Dp = 96.dp
}
