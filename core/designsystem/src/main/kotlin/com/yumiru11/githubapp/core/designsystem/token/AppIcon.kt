package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标体系令牌（Material Symbols + Octicons）。
 *
 * Values sourced from docs/ui-design.md §5: rounded style, weight 300, and
 * the FILL axis drives selected vs. unselected state — selected = filled
 * (FILL = 1), unselected = outlined (FILL = 0); transition 0↔1 animates
 * the state switch. App-wide rule: never use emoji as an icon.
 */
object AppIcon {
    // ── 尺寸档位 ──

    /** 小图标 — 列表行内、辅助语义（对应 GitLight 现有 16dp 用法） */
    val iconSmall: Dp = 16.dp

    /** 标准图标 — 导航栏、工具按钮、状态图标（M3 基准 24dp） */
    val iconMedium: Dp = 24.dp

    /** 大图标 — 空态插图、主要操作按钮 */
    val iconLarge: Dp = 32.dp

    // ── 可变字体轴（§5.2 精细方案；无新依赖时的静态变体同值） ──

    /** FILL 轴：选中态填充 */
    const val FILL_ON: Float = 1f

    /** FILL 轴：未选中描边 */
    const val FILL_OFF: Float = 0f

    /** wght 轴：「稍细」目标值（默认 400） */
    const val WEIGHT_LIGHT: Float = 300f

    /** ROND 轴：圆角最强 */
    const val ROUND_MAX: Float = 100f

    /** GRAD 轴：低强调场景 */
    const val GRAD_LOW: Float = -25f
}
