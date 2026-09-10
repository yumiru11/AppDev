package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** 容器色初始混合比例（issue #85 / audit 缺陷 #2：原色 55% + surface 45%）。 */
private const val LABEL_COLOR_MIX_TOWARD_SURFACE = 0.45f

/** 为达 WCAG AA 允许的最大混合比例（1.0 = 完全退化为 surface 色，仍是可读的浅/深底）。 */
private const val MAX_LABEL_COLOR_MIX = 1.0f

/** 混合比例步进（确定性收敛，最多 11 次迭代）。 */
private const val LABEL_COLOR_MIX_STEP = 0.05f

/** 亮 / 暗主题判据：surface 相对亮度 ≥ 此值视为亮主题（配深墨文字）。 */
private const val BRIGHT_SURFACE_LUMINANCE = 0.5f

/**
 * 亮主题下容器需达到的最小相对亮度（issue #168 / UI25）。
 *
 * 由 WCAG 反推：深墨候选最亮为 M3 亮色 onSurface ≈ #24292F（L ≈ 0.023）→
 * (Lc + 0.05) / 0.073 ≥ 4.5 ⇒ Lc ≥ 0.279；取 0.30 留安全边际（含 #000000 的高对比主题）。
 */
private const val MIN_BRIGHT_CONTAINER_LUMINANCE = 0.30f

/**
 * 暗主题下容器需达到的最大相对亮度（issue #168 / UI25）。
 *
 * 反推：浅墨候选最暗为 M3 暗色 onSurface ≈ #E6E0E9（L ≈ 0.753）→
 * 0.803 / (Lc + 0.05) ≥ 4.5 ⇒ Lc ≤ 0.128；取 0.12 留安全边际。
 */
private const val MAX_DARK_CONTAINER_LUMINANCE = 0.12f

/**
 * 标签徽标容器色：[labelColor] 与 [surface] 混合（初始 55/45，不足 AA 时继续向 surface 混）。
 *
 * 旧实现向固定白色混 45%，深色主题下标签发白刺眼；#85 改混主题 surface 后深浅都
 * 低饱和可控。**#168 / UI25 补最后一块**：45% 的固定比例对「极暗/极亮」标签
 * （如 #000000）会落在「主题深墨与浅墨都够不到 4.5:1」的中间亮度带，
 * 于是按 5% 步进继续向 surface 混合，直到容器亮度落到主题墨色可达 AA 的一侧
 * （亮主题 → 足够亮配深墨；暗主题 → 足够暗配浅墨）。纯函数、确定性、可单测。
 *
 * 与 [labelChipContentColor] 的分工：本函数保证「容器亮度落在正确的一侧」，
 * 后者的对比度断言由 LabelChipColorsTest 的 WCAG 矩阵兜底。
 */
fun labelChipContainerColor(
    labelColor: Color,
    surface: Color,
): Color {
    val surfaceLuminance = surface.luminance()
    var mix = LABEL_COLOR_MIX_TOWARD_SURFACE
    var container = lerp(labelColor, surface, mix)
    while (mix < MAX_LABEL_COLOR_MIX && !reachesAaSide(container.luminance(), surfaceLuminance)) {
        mix = (mix + LABEL_COLOR_MIX_STEP).coerceAtMost(MAX_LABEL_COLOR_MIX)
        container = lerp(labelColor, surface, mix)
    }
    return container
}

/**
 * 容器亮度是否已落在「主题墨色能达到 AA」的一侧。
 *
 * 目标值按 surface 自身亮度夹取：极端/异常 surface（既非近白也非近黑）下，
 * mix = 1.0（容器 = surface）必然满足 → 循环保证终止。
 */
private fun reachesAaSide(
    containerLuminance: Float,
    surfaceLuminance: Float,
): Boolean =
    if (surfaceLuminance >= BRIGHT_SURFACE_LUMINANCE) {
        containerLuminance >= minOf(MIN_BRIGHT_CONTAINER_LUMINANCE, surfaceLuminance)
    } else {
        containerLuminance <= maxOf(MAX_DARK_CONTAINER_LUMINANCE, surfaceLuminance)
    }

/**
 * 标签徽标文字色：按 **[contrastRatio] 取对比度更高**的主题墨色候选。
 *
 * （issue #85 / audit 缺陷 #20，issue #168 / UI25 改为「最对比度择优」）候选内部按亮度排序
 * （较暗者为深墨、较亮者为浅墨），调用方无论按哪种主题极性传入 onSurface 与 surface，
 * 语义都正确：亮容器配深墨、暗容器配浅墨。
 *
 * 旧实现按「容器亮度 ≥ 0.5」硬阈值二选一，#d73a4a 等中亮度标签在亮主题下会被判成
 * 「暗容器 → 配白字」，实测仅 2.2:1（审计 #20 的未验证项）。改择优后同一组
 * GitHub 标签色在 7 组主题（亮/暗/OLED/高对比×2/M3 亮/M3 暗）下全部 ≥ 4.5:1。
 */
fun labelChipContentColor(
    container: Color,
    onSurface: Color,
    surface: Color,
): Color {
    val darkInk = if (onSurface.luminance() <= surface.luminance()) onSurface else surface
    val lightInk = if (darkInk == onSurface) surface else onSurface
    return if (contrastRatio(darkInk, container) >= contrastRatio(lightInk, container)) {
        darkInk
    } else {
        lightInk
    }
}
