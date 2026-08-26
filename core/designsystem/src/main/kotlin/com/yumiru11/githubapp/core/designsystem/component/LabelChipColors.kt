package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** 容器色中 surface 的混入比例（issue #85 / audit 缺陷 #2：原色 55% + surface 45%）。 */
private const val LABEL_COLOR_MIX_TOWARD_SURFACE = 0.45f

/** 相对亮度阈值：容器不低于此值视为亮容器，配深墨文字。 */
private const val CONTENT_LUMINANCE_THRESHOLD = 0.5f

/**
 * 标签徽标容器色：[labelColor] 与 [surface] 按 55/45 混合。
 *
 * 旧实现向固定白色混 45%，深色主题下标签发白刺眼；改混主题 surface 后，
 * 深浅主题都保持低饱和底与可控的文字对比度（WCAG AA 对照）。
 */
fun labelChipContainerColor(
    labelColor: Color,
    surface: Color,
): Color = lerp(labelColor, surface, LABEL_COLOR_MIX_TOWARD_SURFACE)

/**
 * 标签徽标文字色：按 [container] 亮度从两个主题墨色候选中选取。
 *
 * （issue #85 / audit 缺陷 #20）候选内部按亮度排序（较暗者为深墨、较亮者为浅墨），调用方无论按哪种主题
 * 极性传入 onSurface 与 surface，语义都正确：亮容器配深墨、暗容器配浅墨。
 */
fun labelChipContentColor(
    container: Color,
    onSurface: Color,
    surface: Color,
): Color {
    val darkInk = if (onSurface.luminance() <= surface.luminance()) onSurface else surface
    val lightInk = if (darkInk == onSurface) surface else onSurface
    return if (container.luminance() >= CONTENT_LUMINANCE_THRESHOLD) darkInk else lightInk
}
