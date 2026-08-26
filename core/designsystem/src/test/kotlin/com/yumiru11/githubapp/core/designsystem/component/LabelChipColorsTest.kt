package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LabelChip 混色纯函数单测（issue #85 / audit 缺陷 #2 与 #20）：
 * 容器色向主题 surface 混合 45%（不再固定混白），文字色按容器亮度动态选取。
 */
class LabelChipColorsTest {
    private val labelRed = Color(0xFFD73A4A)
    private val black = Color(0xFF000000)
    private val white = Color(0xFFFFFFFF)

    // container：向 surface 混合 45%

    @Test
    fun labelChipContainerColor_coloredLabel_mixesFortyFivePercentTowardSurface() {
        val surface = black
        val mixed = labelChipContainerColor(labelColor = labelRed, surface = surface)
        assertEquals(lerp(labelRed, surface, 0.45f), mixed)
    }

    @Test
    fun labelChipContainerColor_darkThemeSurface_pullsTowardDarkNotWhite() {
        // 深色主题 surface 近黑：混合结果应比原 label 更暗（旧实现混白会变亮）
        val darkSurface = Color(0xFF0E0E10)
        val mixed = labelChipContainerColor(labelColor = labelRed, surface = darkSurface)
        assertTrue(mixed.luminance() < labelRed.luminance())
    }

    // content：按容器亮度动态选 on 色

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
}
