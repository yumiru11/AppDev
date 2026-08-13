package com.yumiru11.githubapp.core.markdown.webview

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import okhttp3.OkHttpClient

/**
 * WebView 高保真兜底渲染组件（plan.md §2.9 / T8）。
 *
 * 用法：T9 等宿主在 [FeatureDetector] 判定复杂内容后调用本组件渲染。
 * 数据源优先级由调用方决定（服务端 HTML 优先 → 离线 markdown-it 兜底）。
 *
 * 安全（plan.md §2.14 红线）：
 * - WebView settings 全锁（[WebViewSecurity.apply]）
 * - 资源经 WebViewAssetLoader 加载（appassets.androidplatform.net 域，禁 file://）
 * - token 绝不进入 HTML/JS（仅由 [PrivateImageInterceptor] 加到图片网络请求）
 *
 * @param sanitizedHtml 已清洗内容（SERVER_HTML 模式：服务端 HTML；OFFLINE 模式：原始 markdown）
 * @param tokenProvider OAuth token 提供方（私有图床白名单拦截用；游客返回 null）
 * @param bridgeCallback JS bridge 白名单回调（链接/代码/图片/复选框/高度）
 * @param renderMode 渲染模式（默认 SERVER_HTML）
 * @param modifier Modifier
 * @param httpClient 复用 OkHttp（私有图床代理请求用；默认 null 表示不代理，图直通）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewMarkdownRenderer(
    sanitizedHtml: String,
    tokenProvider: () -> String?,
    bridgeCallback: MarkdownBridgeCallback,
    modifier: Modifier = Modifier,
    httpClient: OkHttpClient? = null,
    renderMode: RenderMode = RenderMode.SERVER_HTML,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val tokens =
        remember(isDark, colorScheme) {
            MarkdownThemeTokens.fromColorScheme(colorScheme, isDark = isDark)
        }

    val assetLoader =
        remember(context) {
            WebViewAssetLoader
                .Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }

    var measuredHeight by remember { mutableIntStateOf(0) }

    // 包装 callback：onHeightChanged 同步更新本地高度状态，其余事件转发至原 callback
    val heightAwareCallback =
        remember(bridgeCallback) {
            object : MarkdownBridgeCallback by bridgeCallback {
                override fun onHeightChanged(heightPx: Int) {
                    measuredHeight = heightPx
                    bridgeCallback.onHeightChanged(heightPx)
                }
            }
        }
    val bridge = remember(heightAwareCallback) { MarkdownBridge(heightAwareCallback) }

    val heightDp =
        if (measuredHeight > 0) {
            val density = context.resources.displayMetrics.density
            (measuredHeight / density).toInt().dp
        } else {
            200.dp
        }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightDp),
        factory = { ctx ->
            WebView(ctx).apply {
                WebViewSecurity.apply(this)
                addJavascriptInterface(bridge, "AndroidBridge")
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                            httpClient?.let { client ->
                                return PrivateImageInterceptor(tokenProvider, client).intercept(request)
                            }
                            return null
                        }
                    }
                webChromeClient = WebChromeClient()
            }
        },
        update = { webView ->
            val html = WebViewHtmlBuilder.build(sanitizedHtml, tokens, renderMode)
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/",
                html,
                "text/html",
                "utf-8",
                null,
            )
        },
        onReset = { webView ->
            // 主题变更时注入新令牌（plan.md §2.9 Kotlin→JS updateTheme）
            val themeVars =
                tokens
                    .toCssVariables()
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
            val themeJs =
                "if(window.AndroidBridge&&window.AndroidBridge.updateTheme)" +
                    "{AndroidBridge.updateTheme('$themeVars');}"
            webView.evaluateJavascript(themeJs, null)
        },
    )
}
