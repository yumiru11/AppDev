package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 容器色档位策略（背景图 + 毛玻璃的根因修复）。
 *
 * 红→绿验证：把 [resolveAppSurfaces] 的 \`!backgroundImageEnabled\` 分支去掉（恒半透明），
 * 本文件前两个"不透明档"断言立即失败；把 OLED/高对比的强制不透明分支去掉，
 * 后两个断言失败。
 */
class AppSurfacesTest {
    private val light = lightColorScheme()
    private val dark = darkColorScheme()

    private companion object {
        /** Compose 颜色按 8 位通道存储，浮点 alpha 断言需带容差。 */
        const val ALPHA_TOLERANCE = 1f / 255f
    }

    @Test
    fun resolveAppSurfaces_withoutBackgroundImage_isFullyOpaque() {
        val surfaces = resolveAppSurfaces(light, backgroundImageEnabled = false)
        assertFalse(surfaces.translucent)
        assertEquals(light.background, surfaces.screen)
        assertEquals(light.surfaceContainerLow, surfaces.card)
        assertEquals(light.surfaceContainerHigh, surfaces.cardHigh)
        assertEquals(light.surface, surfaces.bar)
        assertEquals(1f, surfaces.sheet.alpha)
    }

    @Test
    fun resolveAppSurfaces_withBackgroundImage_isTranslucentPerRole() {
        val surfaces = resolveAppSurfaces(light, backgroundImageEnabled = true)
        assertTrue(surfaces.translucent)
        // Compose Color 的 alpha 是 8 位量化存储（0.55f → 0.5490196），故用 1/255 容差
        assertEquals(AppSurfaceAlpha.SCREEN, surfaces.screen.alpha, ALPHA_TOLERANCE)
        assertEquals(AppSurfaceAlpha.CARD, surfaces.card.alpha, ALPHA_TOLERANCE)
        assertEquals(AppSurfaceAlpha.CARD_HIGH, surfaces.cardHigh.alpha, ALPHA_TOLERANCE)
        assertEquals(AppSurfaceAlpha.BAR, surfaces.bar.alpha, ALPHA_TOLERANCE)
        // sheet / dialog 恒不透明：#201 P0（叠印）不得复发
        assertEquals(1f, surfaces.sheet.alpha, ALPHA_TOLERANCE)
    }

    @Test
    fun resolveAppSurfaces_backgroundImageWithOled_isForcedOpaque() {
        val surfaces = resolveAppSurfaces(light, backgroundImageEnabled = true, oled = true)
        assertFalse(surfaces.translucent)
        assertEquals(1f, surfaces.screen.alpha)
        assertEquals(1f, surfaces.card.alpha)
    }

    @Test
    fun resolveAppSurfaces_backgroundImageWithHighContrast_isForcedOpaque() {
        val surfaces = resolveAppSurfaces(dark, backgroundImageEnabled = true, highContrast = true)
        assertFalse(surfaces.translucent)
        assertEquals(1f, surfaces.screen.alpha)
        assertEquals(1f, surfaces.bar.alpha)
    }

    @Test
    fun resolveAppSurfaces_darkScheme_keepsSchemeColors() {
        val surfaces = resolveAppSurfaces(dark, backgroundImageEnabled = true)
        // 半透明只改 alpha，不改基色（深浅色/动态取色都能正确透出）
        assertEquals(dark.background.red, surfaces.screen.red, 0.0001f)
        assertEquals(dark.background.green, surfaces.screen.green, 0.0001f)
        assertEquals(dark.background.blue, surfaces.screen.blue, 0.0001f)
    }
}
