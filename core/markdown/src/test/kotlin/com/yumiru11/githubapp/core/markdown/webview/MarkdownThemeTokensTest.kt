package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * MarkdownThemeTokens 单元测试（纯 JVM，无 Robolectric）。
 *
 * 验证 Material You CSS 令牌注入：明暗两态变量值正确（hex 格式、key 完整）。
 * 命名遵循 methodName_scenario_expectedBehavior 约定。
 */
class MarkdownThemeTokensTest {
    @Test
    fun toCssVariables_lightScheme_containsAllSemanticKeys() {
        val tokens = MarkdownThemeTokens.fromLightScheme()

        val css = tokens.toCssVariables()

        REQUIRED_CSS_KEYS.forEach { key ->
            assertTrue("light scheme CSS must define $key", css.contains(key))
        }
    }

    @Test
    fun toCssVariables_darkScheme_containsAllSemanticKeys() {
        val tokens = MarkdownThemeTokens.fromDarkScheme()

        val css = tokens.toCssVariables()

        REQUIRED_CSS_KEYS.forEach { key ->
            assertTrue("dark scheme CSS must define $key", css.contains(key))
        }
    }

    @Test
    fun toCssVariables_lightVsDark_onSurfaceDiffers() {
        val lightOnSurface = MarkdownThemeTokens.fromLightScheme().onSurface
        val darkOnSurface = MarkdownThemeTokens.fromDarkScheme().onSurface

        // onSurface 在浅色与深色主题下取值应不同（深色文字 vs 亮色文字）
        assertFalse("onSurface must differ between light and dark", lightOnSurface == darkOnSurface)
    }

    @Test
    fun toCssVariables_valuesAreHexFormatted() {
        val tokens = MarkdownThemeTokens.fromLightScheme()

        val css = tokens.toCssVariables()

        // 每个变量值应为 #RRGGBB hex 格式（大写或小写均可）
        val hexLineRegex = Regex("""--[\w-]+:\s*#[0-9a-fA-F]{6}\s*;""")
        val matches = hexLineRegex.findAll(css).count()
        assertTrue("expected several hex-formatted CSS vars, got $matches", matches >= REQUIRED_CSS_KEYS.size)
    }

    @Test
    fun fromColorScheme_lightColors_matchesFromLightScheme() {
        val scheme = lightColorScheme()

        val tokens = MarkdownThemeTokens.fromColorScheme(scheme, isDark = false)

        assertEquals(toHex(scheme.onSurface), tokens.onSurface)
        assertEquals(toHex(scheme.primary), tokens.primary)
    }

    @Test
    fun fromColorScheme_darkColors_matchesFromDarkScheme() {
        val scheme = darkColorScheme()

        val tokens = MarkdownThemeTokens.fromColorScheme(scheme, isDark = true)

        assertEquals(toHex(scheme.onSurface), tokens.onSurface)
        assertEquals(toHex(scheme.primary), tokens.primary)
    }

    @Test
    fun toCssVariables_primaryValueMatchesToken() {
        val tokens =
            MarkdownThemeTokens(
                primary = "#445566",
                onSurface = "#112233",
                surface = "#FFFFFF",
                surfaceContainerLow = "#F0F0F0",
                surfaceContainerHigh = "#E0E0E0",
                outlineVariant = "#CCCCCC",
                isDark = false,
            )

        val css = tokens.toCssVariables()

        assertTrue("onSurface var must carry hex value", css.contains("--md-sys-color-on-surface: #112233"))
        assertTrue("primary var must carry hex value", css.contains("--md-sys-color-primary: #445566"))
    }

    private companion object {
        val REQUIRED_CSS_KEYS =
            listOf(
                "--md-sys-color-primary",
                "--md-sys-color-on-surface",
                "--md-sys-color-surface",
                "--md-sys-color-surface-container-low",
                "--md-sys-color-surface-container-high",
                "--md-sys-color-outline-variant",
            )

        fun toHex(color: Color): String {
            val r = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
            return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
        }
    }
}
