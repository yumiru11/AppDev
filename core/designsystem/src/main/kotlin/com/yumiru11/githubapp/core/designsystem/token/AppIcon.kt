package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.datastore.model.IconStyle

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

/**
 * 全局图标风格（由 app 层 AppThemeHost 从 UserPreferencesRepository.iconStyle 注入，
 * issue #168 / UI12）。消费侧只读本 CompositionLocal——见
 * [com.yumiru11.githubapp.core.designsystem.icon.AppIcon]。
 *
 * static：值为偏好快照，切换时整棵子树重组（图标风格不变则无需逐帧读取）；
 * 默认 [IconStyle.ROUNDED]（ui-design §5 默认圆润），截图测试/预览不提供时行为与默认一致。
 */
val LocalIconStyle = staticCompositionLocalOf { IconStyle.ROUNDED }
