package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 4dp 基准栅格的间距 scale（设计系统 Batch 3，`implementation-plan.md` §2 决策 7）。
 *
 * 唯一入口是 [AppDimens.spacing]，调用点一律写 `AppDimens.spacing.xs` 这类形式；
 * 本类只是值载体，不做密度/字号缩放（结构间距不随阅读偏好变化）。
 * 级别命名与计划一致：xs/s/m/l/xl/xxl 对应 4/8/12/16/24/32 dp。
 *
 * @see AppDimens.spacing
 */
class SpacingScale internal constructor() {
    /** 4dp —— 微间隙（图标与文本、紧密堆叠元素之间） */
    val xs: Dp = 4.dp

    /** 8dp —— 小间隙（列表项内部、chip 行距） */
    val s: Dp = 8.dp

    /** 12dp —— 中间隙（卡片内边距、组内条目间距） */
    val m: Dp = 12.dp

    /** 16dp —— 大间隙（屏幕内容内边距，等价旧名 [AppDimens.contentPadding]） */
    val l: Dp = 16.dp

    /** 24dp —— 超大间隙（分节之间、段落间距） */
    val xl: Dp = 24.dp

    /** 32dp —— 页级间隙（空态与内容区之间、超大分隔） */
    val xxl: Dp = 32.dp
}
