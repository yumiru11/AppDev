package com.yumiru11.githubapp.core.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubTextMateThemeTest {
    @Test
    fun buildColorsJson_darkTheme_usesVsCodeDarkForegroundAndTransparentBackground() {
        val json = GitHubTextMateTheme.buildColorsJson(isDark = true)

        assertTrue(json.contains("\"editor.foreground\": \"#D4D4D4\""))
        assertTrue(json.contains("\"editor.background\": \"#00000000\""))
    }

    @Test
    fun buildColorsJson_lightTheme_usesVsCodeLightForeground() {
        val json = GitHubTextMateTheme.buildColorsJson(isDark = false)

        assertTrue(json.contains("\"editor.foreground\": \"#000000\""))
        assertFalse(json.contains("#D4D4D4"))
    }
}
