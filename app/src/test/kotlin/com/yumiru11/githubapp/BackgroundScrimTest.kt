package com.yumiru11.githubapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 背景图蒙版换算（#167 / UI04）。
 *
 * **纯 JVM 测试**：这是本特性里唯一有"规则"的部分（§7.4 拍板了浅色变白/深色变黑/
 * 深色额外压暗），抽成纯函数才能在这里断言，而不是靠截图上肉眼比对。
 */
class BackgroundScrimTest {
    @Test
    fun scrimAlpha_lightTheme_isOneMinusOpacity() {
        assertEquals(0.75f, BackgroundScrim.scrimAlpha(opacity = 0.25f, darkTheme = false), 0.0001f)
        assertEquals(0.5f, BackgroundScrim.scrimAlpha(opacity = 0.5f, darkTheme = false), 0.0001f)
    }

    @Test
    fun scrimAlpha_darkTheme_isDimmerThanLightForSameOpacity() {
        // §7.4「深色自动降低亮度」：同样的不透明度，深色下蒙版更厚（图更淡）
        val light = BackgroundScrim.scrimAlpha(opacity = 0.5f, darkTheme = false)
        val dark = BackgroundScrim.scrimAlpha(opacity = 0.5f, darkTheme = true)
        assertTrue("深色蒙版应比浅色更厚（$dark > $light）", dark > light)
    }

    @Test
    fun scrimAlpha_darkTheme_appliesDimFactor() {
        // 0.5 × 0.6 = 0.3 有效不透明度 → 蒙版 0.7
        assertEquals(0.7f, BackgroundScrim.scrimAlpha(opacity = 0.5f, darkTheme = true), 0.0001f)
        assertEquals(0.85f, BackgroundScrim.scrimAlpha(opacity = 0.25f, darkTheme = true), 0.0001f)
    }

    @Test
    fun scrimAlpha_higherOpacity_thinsTheScrim() {
        val low = BackgroundScrim.scrimAlpha(opacity = 0.1f, darkTheme = false)
        val high = BackgroundScrim.scrimAlpha(opacity = 0.6f, darkTheme = false)
        assertTrue("不透明度越高，蒙版越薄（图越明显）", high < low)
    }

    @Test
    fun scrimAlpha_outOfRange_clampsToValidRange() {
        // 防御：调用方理论上已 coerce，但纯函数不该产出越界 alpha
        assertEquals(0f, BackgroundScrim.scrimAlpha(opacity = 5f, darkTheme = false), 0.0001f)
        assertEquals(1f, BackgroundScrim.scrimAlpha(opacity = -1f, darkTheme = false), 0.0001f)
    }
}
