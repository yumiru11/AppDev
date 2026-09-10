package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.yumiru11.githubapp.core.datastore.model.IconStyle

/**
 * 语义图标的风格变体集合（ui-design §5.2 / ADR-0004，issue #168 / UI12）。
 *
 * 一个语义图标（如「首页」）在 Material Symbols 的家族里各有一个 ImageVector：
 * outlined（线性）/ outlinedFilled（线性实心）/ rounded（圆润）/ roundedFilled（圆润实心）。
 * [AppIcon] 按当前 [IconStyle] 与选中态从中解析出最终矢量——**消费方只写一次语义图标**，
 * 风格切换对全应用即时生效（设置页「图标风格」）。
 *
 * 语义约定（ui-design §5.1 用户拍板「选中态 filled、未选中 outlined」）：
 * - [IconStyle.OUTLINED] → 线性家族（选中态取线性实心变体）
 * - [IconStyle.ROUNDED] → 圆润家族（默认；选中态取圆润实心变体）
 * - [IconStyle.FILLED] → 恒为圆润实心（Material Symbols 的填充即 FILL=1，无独立 Filled 家族）
 *
 * GitHub 专属图标（Octicons）没有风格家族，用 [single] 构造：三档共用同一矢量，
 * 选中态由 M3 导航项的胶囊指示器与着色表达（ADR-0006）。
 */
data class AppIconSpec(
    val outlined: ImageVector,
    val outlinedFilled: ImageVector,
    val rounded: ImageVector,
    val roundedFilled: ImageVector,
) {
    /**
     * 解析当前应绘制的矢量（纯函数，单测锁定 3 风格 × 2 选中态 = 6 分支）。
     *
     * @param style 当前图标风格（来自 [com.yumiru11.githubapp.core.designsystem.token.LocalIconStyle]）
     * @param selected 选中态（底部导航/分区条等）
     */
    fun vectorFor(
        style: IconStyle,
        selected: Boolean,
    ): ImageVector =
        when {
            style == IconStyle.FILLED -> roundedFilled
            style == IconStyle.OUTLINED -> if (selected) outlinedFilled else outlined
            else -> if (selected) roundedFilled else rounded
        }

    companion object {
        /** 单一矢量图标（Octicons 等无风格变体）：三档风格与两种选中态共用同一矢量。 */
        fun single(vector: ImageVector): AppIconSpec = AppIconSpec(vector, vector, vector, vector)
    }
}
