package com.yumiru11.githubapp.core.markdown.webview

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewHtmlBuilderFusionTest {
    @Test
    fun build_fusionTheme_includesColorSchemeMetaAndGithubCss() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>x</p>",
                themeVariables = MaterialYouFusionMapper.buildCss(lightColorScheme(), isDark = false),
                isDark = false,
            )

        assertTrue(html.contains("<meta name=\"color-scheme\" content=\"light dark\">"))
        assertTrue(html.contains("github-markdown.css"))
        assertTrue(html.contains("markdown-you.css"))
    }

    @Test
    fun build_fusionTheme_setsDataThemeOnHtmlAndBody() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>x</p>",
                themeVariables = MaterialYouFusionMapper.buildCss(darkColorScheme(), isDark = true),
                isDark = true,
            )

        assertTrue(html.contains("<html lang=\"en\" data-theme=\"dark\">"))
        assertTrue(html.contains("<body data-theme=\"dark\">"))
    }

    @Test
    fun build_fusionTheme_injectsMaterialVariablesAfterGithubCss() {
        val variables = MaterialYouFusionMapper.buildCss(lightColorScheme(), isDark = false)
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<p>x</p>",
                themeVariables = variables,
                isDark = false,
            )

        val githubCss = html.indexOf("github-markdown.css")
        val themeVars = html.indexOf(variables)
        assertTrue(githubCss > 0)
        assertTrue(themeVars > githubCss)
    }
}
