/*
 * PROTOTYPE ONLY — writes the exact WebViewHtmlBuilder output for headless Chromium screenshots.
 */
package com.yumiru11.githubapp.prototype.readmecomparison

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.yumiru11.githubapp.core.markdown.webview.MaterialYouFusionMapper
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.markdown.webview.WebViewHtmlBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebViewHeadlessHtmlWriterTest {
    @Test
    fun writeHeadlessHtml_lightAndDark_useSameOfflineChain() {
        val markdown = File("src/main/assets/complex-readme.md").readText()
        val outputDir = File("build/headless").apply { mkdirs() }

        writeHtml(outputDir, "readme-webview-light.html", markdown, isDark = false)
        writeHtml(outputDir, "readme-webview-dark.html", markdown, isDark = true)

        assertTrue(File(outputDir, "readme-webview-light.html").isFile)
        assertTrue(File(outputDir, "readme-webview-dark.html").isFile)
    }

    private fun writeHtml(
        outputDir: File,
        name: String,
        markdown: String,
        isDark: Boolean,
    ) {
        val scheme = if (isDark) darkColorScheme() else lightColorScheme()
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = markdown,
                themeVariables = MaterialYouFusionMapper.buildCss(scheme, isDark),
                isDark = isDark,
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                baseRepoUrl = null,
            )
        File(outputDir, name).writeText(html)
    }
}
