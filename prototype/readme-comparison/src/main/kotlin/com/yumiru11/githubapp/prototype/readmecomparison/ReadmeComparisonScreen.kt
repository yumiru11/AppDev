/*
 * PROTOTYPE ONLY — disposable A/B comparison shell. Do not reuse in production modules.
 */
@file:Suppress("EmptyFunctionBlock") // Link callbacks are intentionally inert in the visual comparison shell.

package com.yumiru11.githubapp.prototype.readmecomparison

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

private enum class ComparisonVersion {
    WEBVIEW,
    NATIVE,
}

/**
 * A/B comparison shell. Version A renders the shared fixture through the existing WebView
 * offline chain; version B starts on the existing native viewer and is replaced by the
 * enhanced viewer as the prototype progresses.
 */
@Composable
fun ReadmeComparisonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markdown = remember(context) { PrototypeReadme.load(context) }
    var version by remember { mutableStateOf(ComparisonVersion.WEBVIEW) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.prototype_readme_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        PrimaryTabRow(selectedTabIndex = version.ordinal) {
            Tab(
                selected = version == ComparisonVersion.WEBVIEW,
                onClick = { version = ComparisonVersion.WEBVIEW },
                text = { Text(stringResource(R.string.prototype_readme_tab_webview)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Tab(
                selected = version == ComparisonVersion.NATIVE,
                onClick = { version = ComparisonVersion.NATIVE },
                text = { Text(stringResource(R.string.prototype_readme_tab_native)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (version) {
                ComparisonVersion.WEBVIEW -> {
                    WebViewMarkdownRenderer(
                        sanitizedHtml = markdown,
                        tokenProvider = { null },
                        bridgeCallback = InertBridgeCallback,
                        renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                        baseRepoUrl = "https://appassets.androidplatform.net/",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                ComparisonVersion.NATIVE -> {
                    MarkdownViewer(
                        markdown = markdown,
                        onInternalLink = { },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private object InertBridgeCallback : MarkdownBridgeCallback {
    override fun onExternalLink(url: String) {}

    override fun onInternalLink(parsed: ParsedUrl) {}

    override fun onCodeCopy(code: String) {}

    override fun onImageClick(src: String) {}

    override fun onCheckboxClick(
        index: Int,
        checked: Boolean,
    ) {}

    override fun onHeightChanged(heightPx: Int) {}
}
