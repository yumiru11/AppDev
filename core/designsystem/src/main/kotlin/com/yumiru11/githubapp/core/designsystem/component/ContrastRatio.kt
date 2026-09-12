package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG 2.1 AA 正文（< 18pt）最低对比度 4.5:1（ui-design §8 对比度达标）。 */
const val WCAG_AA_NORMAL_TEXT_CONTRAST = 4.5f

/**
 * WCAG 2.1 相对亮度对比度：(L亮 + 0.05) / (L暗 + 0.05)，取值 1:1 ~ 21:1。
 *
 * 与 Compose 的 [luminance] 同口径（sRGB 传递函数线性化后按 0.2126 / 0.7152 / 0.0722
 * 加权），纯函数、零新依赖——生产与测试共用同一份实现，避免「测试自算一套、生产另一套」
 * 的对不上（issue #168 / UI25）。
 */
fun contrastRatio(
    foreground: Color,
    background: Color,
): Float {
    val foregroundLuminance = foreground.luminance()
    val backgroundLuminance = background.luminance()
    return (max(foregroundLuminance, backgroundLuminance) + 0.05f) /
        (min(foregroundLuminance, backgroundLuminance) + 0.05f)
}

/**
 * 该前景/背景组合是否达到 WCAG AA（≥ [WCAG_AA_NORMAL_TEXT_CONTRAST]）。
 *
 * **保留声明（DEAD-1 核验）**：当前无生产调用方；它是 LabelChipColors 跨主题 AA 守卫
 * （LabelChipColorsTest）的共用度量口径。删除会迫使测试自算阈值，违背本文件
 * 「生产与测试共用同一实现」的初衷，故有意保留。
 */
fun meetsWcagAa(
    foreground: Color,
    background: Color,
): Boolean = contrastRatio(foreground, background) >= WCAG_AA_NORMAL_TEXT_CONTRAST
