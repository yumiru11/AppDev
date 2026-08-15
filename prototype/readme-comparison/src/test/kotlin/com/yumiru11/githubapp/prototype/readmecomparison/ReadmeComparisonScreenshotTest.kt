/*
 * PROTOTYPE ONLY — Roborazzi baselines for the B (native enhanced) renderer.
 * Version A is captured by headless Chromium (Robolectric WebView cannot rasterize pixels).
 */
package com.yumiru11.githubapp.prototype.readmecomparison

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test

class ReadmeComparisonScreenshotTest : ScreenshotTest() {
    @Test
    fun readmeComparison_nativeLight_matchesBaseline() {
        captureScreenshot("readmeComparison_nativeLight", darkTheme = false) { ReadmeFixture() }
    }

    @Test
    fun readmeComparison_nativeDark_matchesBaseline() {
        captureScreenshot("readmeComparison_nativeDark", darkTheme = true) { ReadmeFixture() }
    }

    @Composable
    private fun ReadmeFixture() {
        val context = LocalContext.current
        val markdown = remember(context) { PrototypeReadme.load(context) }
        EnhancedMarkdownViewer(
            markdown = markdown,
            imageTransformer = remember(context) { AssetMarkdownImageTransformer(context) },
            darkTheme = isDarkTheme(),
            horizontalScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().width(720.dp),
        )
    }

    @Composable
    private fun isDarkTheme(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()
}
