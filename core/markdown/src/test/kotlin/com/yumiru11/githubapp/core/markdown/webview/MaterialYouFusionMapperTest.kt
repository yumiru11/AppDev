package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialYouFusionMapperTest {
    @Test
    fun buildCss_lightScheme_containsMaterialRoleMappings() {
        val css = MaterialYouFusionMapper.buildCss(lightColorScheme(), isDark = false)

        assertTrue(css.contains("--fgColor-default:"))
        assertTrue(css.contains("--bgColor-default:"))
        assertTrue(css.contains("--borderColor-default:"))
        assertTrue(css.contains("--md-sys-color-primary:"))
        assertTrue(css.contains("[data-theme=\"light\"]"))
    }

    @Test
    fun buildCss_darkScheme_switchesThemeSelectorAndValues() {
        val light = MaterialYouFusionMapper.buildCss(lightColorScheme(), isDark = false)
        val dark = MaterialYouFusionMapper.buildCss(darkColorScheme(), isDark = true)

        assertTrue(dark.contains("[data-theme=\"dark\"]"))
        assertFalse(dark.contains("[data-theme=\"light\"]"))
        assertTrue(light.contains("--bgColor-default: #"))
        assertTrue(dark.contains("--bgColor-default: #"))
    }

    @Test
    fun buildCss_prettylightsSyntax_keepsGitHubOriginalColors() {
        val light = MaterialYouFusionMapper.buildCss(lightColorScheme(), isDark = false)
        val dark = MaterialYouFusionMapper.buildCss(darkColorScheme(), isDark = true)

        assertTrue(light.contains("--color-prettylights-syntax-keyword: #cf222e;"))
        assertTrue(light.contains("--color-prettylights-syntax-string: #0a3069;"))
        assertTrue(dark.contains("--color-prettylights-syntax-keyword: #ff7b72;"))
        assertTrue(dark.contains("--color-prettylights-syntax-string: #a5d6ff;"))
    }

    @Test
    fun buildStartScript_lightTheme_injectsOnlyGeneratedValues() {
        val script = MaterialYouFusionMapper.buildStartScript(lightColorScheme(), isDark = false)

        assertTrue(script.contains("data-theme"))
        assertTrue(script.contains("'light'"))
        assertTrue(script.contains("--fgColor-default"))
        assertFalse(script.contains("</script"))
        assertFalse(script.contains("token", ignoreCase = true))
    }

    @Test
    fun buildStartScript_darkTheme_setsDarkDataTheme() {
        val script = MaterialYouFusionMapper.buildStartScript(darkColorScheme(), isDark = true)

        assertTrue(script.contains("'dark'"))
        assertFalse(script.contains("'light'"))
    }
}
