package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LabelChip 混色 / 对比度纯函数单测（issue #85 / audit 缺陷 #2 与 #20，issue #168 / UI25）。
 *
 * 三级断言：
 * 1. [contrastRatio] 本身与 WCAG 2.1 已知取值一致（黑/白 = 21:1，同色 = 1:1）
 * 2. 容器色混合行为（初始 45% 向 surface、暗色主题不变白、不足 AA 时继续混到达标）
 * 3. **WCAG AA 矩阵**：GitHub 标签色典型取值 × 真实主题（亮/暗/OLED/高对比/M3 基准）
 *    全部 ≥ 4.5:1
 */
class LabelChipColorsTest {
    private val black = Color(0xFF000000)
    private val white = Color(0xFFFFFFFF)

    /** GitHub 标签色典型取值（issues 上的热门 label 色 + 边界黑/白）。 */
    private val gitHubLabelColors =
        listOf(
            0xFFFFFFFFL, // 白
            0xFF000000L, // 黑（最坏边界）
            0xFFD73A4AL, // red
            0xFF0E8A16L, // green
            0xFF1D76DBL, // blue
            0xFFFBCA04L, // yellow
            0xFF0366D6L, // blue dark
            0xFF6F42C1L, // purple
            0xFFFFD33DL, // yellow bright
            0xFF0075CAL, // blue light
            0xFFE99695L, // salmon
            0xFFC2E0C6L, // green light
            0xFFBFD4F2L, // blue pale
            0xFFC5DEF5L, // blue pale 2
            0xFFD4C5F9L, // purple light
            0xFFB60205L, // red dark
            0xFF5319E7L, // purple deep
            0xFF84B6EBL, // blue soft
            0xFFFEF2C0L, // yellow pale
            0xFFF9D0C4L, // peach
            0xFFCC317CL, // magenta
            0xFF0052CCL, // blue deep
            0xFFA2EEEFL, // cyan
            0xFF7057FFL, // violet
            0xFF008672L, // teal
            0xFFE4E669L, // yellow green
            0xFFEDEDEDL, // gray light
        )

    /** 真实主题 surface / onSurface（core:designsystem ThemeColors + M3 基准动态取色）。 */
    private val themes =
        listOf(
            "light" to (white to Color(0xFF24292F)),
            "dark" to (Color(0xFF0D1117) to Color(0xFFE6EDF3)),
            "oled" to (black to Color(0xFFE6EDF3)),
            "hc-light" to (white to black),
            "hc-dark" to (Color(0xFF010409) to Color(0xFFF0F3F6)),
            "m3-light" to (Color(0xFFFEF7FF) to Color(0xFF1D1B20)),
            "m3-dark" to (Color(0xFF141218) to Color(0xFFE6E0E9)),
        )

    // ── 1. contrastRatio 本身 ─────────────────────────────────────────────

    @Test
    fun contrastRatio_blackOnWhite_isTwentyOne() {
        assertEquals(21f, contrastRatio(black, white), 0.01f)
    }

    @Test
    fun contrastRatio_sameColor_isOne() {
        assertEquals(1f, contrastRatio(Color(0xFF1D76DB), Color(0xFF1D76DB)), 0.001f)
    }

    @Test
    fun contrastRatio_whiteOnBlack_symmetricWithBlackOnWhite() {
        assertEquals(contrastRatio(black, white), contrastRatio(white, black), 0.001f)
    }

    @Test
    fun meetsWcagAa_boundaryPair_separatesAtFortyFiveTenths() {
        // #767676 on white ≈ 4.54:1（AA 通过的经典边界灰）
        assertTrue(meetsWcagAa(Color(0xFF767676), white))
        // #777777 on white ≈ 4.48:1（差一点）
        assertTrue(!meetsWcagAa(Color(0xFF777777), white))
    }

    // ── 2. 容器色混合行为 ─────────────────────────────────────────────────

    @Test
    fun labelChipContainerColor_coloredLabel_mixesFortyFivePercentTowardSurface_whenAlreadyAaSafe() {
        // #1d76db 混 45% 后已落在亮主题可达 AA 的一侧 → 不再追加混合（视觉与 #85 一致）
        val surface = white
        val label = Color(0xFF1D76DB)
        val mixed = labelChipContainerColor(labelColor = label, surface = surface)
        assertEquals(lerp(label, surface, 0.45f), mixed)
    }

    @Test
    fun labelChipContainerColor_darkThemeSurface_pullsTowardDarkNotWhite() {
        // 深色主题 surface 近黑：混合结果应比原 label 更暗（旧实现混白会变亮）
        val darkSurface = Color(0xFF0E0E10)
        val labelRed = Color(0xFFD73A4A)
        val mixed = labelChipContainerColor(labelColor = labelRed, surface = darkSurface)
        assertTrue(mixed.luminance() < labelRed.luminance())
    }

    @Test
    fun labelChipContainerColor_blackLabelOnLightSurface_mixesFurtherUntilAaReachable() {
        // #000000 混 45% 得到 ≈#737373（L≈0.18）：主题深墨/浅墨都够不到 AA 的中间带
        // → 继续向 surface 混，直到亮主题深墨可达（issue #168 / UI25 的兜底策略）
        val surface = white
        val mixed = labelChipContainerColor(labelColor = black, surface = surface)
        assertTrue(
            "black label container must leave the AA dead band: luminance=" + mixed.luminance(),
            mixed.luminance() >= 0.279f,
        )
    }

    @Test
    fun labelChipContainerColor_whiteLabelOnDarkSurface_mixesFurtherUntilAaReachable() {
        // 白标签在暗主题同理：必须暗到浅墨可达 AA
        val darkSurface = Color(0xFF0D1117)
        val mixed = labelChipContainerColor(labelColor = white, surface = darkSurface)
        assertTrue(
            "white label container must leave the AA dead band: luminance=" + mixed.luminance(),
            mixed.luminance() <= 0.128f,
        )
    }

    // ── 3. WCAG AA 矩阵（issue #168 / UI25 核心断言）─────────────────────

    @Test
    fun labelChipColors_gitHubLabelPalette_onEveryTheme_meetsWcagAa() {
        val failures = mutableListOf<String>()
        themes.forEach { (themeName, colors) ->
            val (surface, onSurface) = colors
            gitHubLabelColors.forEach { labelColorValue ->
                val labelColor = Color(labelColorValue)
                val container = labelChipContainerColor(labelColor = labelColor, surface = surface)
                val content = labelChipContentColor(container = container, onSurface = onSurface, surface = surface)
                val ratio = contrastRatio(content, container)
                if (ratio < WCAG_AA_NORMAL_TEXT_CONTRAST) {
                    failures +=
                        "$themeName label=#${labelColorValue.toString(16)} " +
                        "container=#${container.value.toString(16)} ratio=$ratio"
                }
            }
        }
        assertTrue(
            "WCAG AA (>= ${WCAG_AA_NORMAL_TEXT_CONTRAST}:1) violations:\n" +
                failures.joinToString(separator = "\n"),
            failures.isEmpty(),
        )
    }

    // ── 4. 文字色择优（#85 行为保持 + #25 修正）──────────────────────────

    @Test
    fun labelChipContentColor_brightContainer_picksDarkInk() {
        val picked =
            labelChipContentColor(
                container = Color(0xFFCCCCCC),
                onSurface = black,
                surface = white,
            )
        assertEquals(black, picked)
    }

    @Test
    fun labelChipContentColor_darkContainer_picksLightInk() {
        val picked =
            labelChipContentColor(
                container = Color(0xFF333333),
                onSurface = black,
                surface = white,
            )
        assertEquals(white, picked)
    }

    @Test
    fun labelChipContentColor_darkThemePolarity_swappedCandidatesStillCorrect() {
        // 深色主题：onSurface 近白、surface 近黑——排序后语义不变
        val pickedBright =
            labelChipContentColor(
                container = Color(0xFFEEEEEE),
                onSurface = white,
                surface = black,
            )
        assertEquals(black, pickedBright)

        val pickedDark =
            labelChipContentColor(
                container = Color(0xFF222222),
                onSurface = white,
                surface = black,
            )
        assertEquals(white, pickedDark)
    }

    @Test
    fun labelChipContentColor_midLuminanceContainer_picksHigherContrastInkNotThresholdInk() {
        // #d73a4a 混白后的中亮度容器（L≈0.4）：旧阈值法会配白字（≈2.2:1）
        // → 择优后必须配深墨（≈7:1）
        val lightSurface = white
        val onSurface = Color(0xFF24292F)
        val container = labelChipContainerColor(labelColor = Color(0xFFD73A4A), surface = lightSurface)
        val content = labelChipContentColor(container = container, onSurface = onSurface, surface = lightSurface)

        assertEquals(onSurface, content)
        assertTrue(contrastRatio(content, container) >= WCAG_AA_NORMAL_TEXT_CONTRAST)
    }
}
