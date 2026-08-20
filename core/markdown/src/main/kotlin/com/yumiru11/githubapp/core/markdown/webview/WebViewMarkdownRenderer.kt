@file:Suppress("LongMethod") // WebView 装配（安全锁/资产加载器/JS bridge/更新回调）是单体初始化流程，拆分反而碎片化

package com.yumiru11.githubapp.core.markdown.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
 * - SERVER_HTML 内容由 [WebViewHtmlBuilder] 内建 [HtmlSanitizer] 强制清洗（组件责任，
 *   不依赖调用方；离线模式渲染产物由 DOMPurify 二次权威清洗）
 *
 * @param sanitizedHtml 待渲染内容（SERVER_HTML 模式：服务端 HTML，构建时强制清洗；
 *   OFFLINE 模式：原始 markdown）
 * @param tokenProvider OAuth token 提供方（私有图床白名单拦截用；游客返回 null）
 * @param bridgeCallback JS bridge 白名单回调（链接/代码/图片/复选框/高度）
 * @param renderMode 渲染模式（默认 SERVER_HTML）
 * @param modifier Modifier
 * @param httpClient 复用 OkHttp（私有图床代理请求用；默认 null 表示不代理，图直通）
 * @param fillAvailableHeight 占满可用高度（WebView 内部滚动，浏览器式预览；默认 false =
 *   内容高度自适应，宿主滚动）。编辑器预览（T21）用 true。
 */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION") // WebSettingsCompat darkening APIs are deprecated upstream but required by the pre-approved darkening policy.
@Composable
fun WebViewMarkdownRenderer(
    sanitizedHtml: String,
    tokenProvider: () -> String?,
    bridgeCallback: MarkdownBridgeCallback,
    modifier: Modifier = Modifier,
    httpClient: OkHttpClient? = null,
    renderMode: RenderMode = RenderMode.SERVER_HTML,
    baseRepoUrl: String? = null,
    fillAvailableHeight: Boolean = false,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val themeVariables =
        remember(isDark, colorScheme) {
            MaterialYouFusionMapper.buildCss(colorScheme, isDark = isDark)
        }
    val startScript =
        remember(isDark, colorScheme) {
            MaterialYouFusionMapper.buildStartScript(colorScheme, isDark = isDark)
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
            // renderer.js 的 ResizeObserver.contentRect.height 是 WebView 的 CSS px，
            // 而 WebView 的 CSS px 即 dp（不随屏幕 density 缩放）。不能除以 density，
            // 否则容器高度缩到约 1/density，正文下半截（任务列表第二项往后）被裁在
            // WebView 底边之外、评论区紧跟其上（CI issue-long.png-02 实测）。
            measuredHeight.dp
        } else {
            200.dp
        }

    AndroidView(
        modifier =
            if (fillAvailableHeight) {
                modifier.fillMaxSize()
            } else {
                modifier.fillMaxWidth().height(heightDp)
            },
        factory = { ctx ->
            WebView(ctx).apply {
                WebViewSecurity.apply(this)
                // body 背景 transparent（markdown-you.css），此处必须同步透明，
                // 否则 WebView 控件默认白底在深色主题下与页面不融合（2026-08-15 真机验证）。
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // 防双重变暗：页面 CSS 已按 data-theme/prefers-color-scheme 出图，
                // 关闭 WebView 的算法暗化并保留 web-theme 策略（androidx.webkit 1.12.1）。
                // 特性检查：API 30 模拟器/旧 WebView 不支持 AlgorithmicDarkening，
                // 直接调用抛 UnsupportedOperationException 崩溃（2026-08-16 模拟器截图
                // logcat 实证 FATAL EXCEPTION at WebViewMarkdownRenderer.kt:118）。
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                }
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY,
                )
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    // allowedOriginRules 只接受 origin（scheme://host 或 * / *.host），
                    // 不接受路径通配符 —— "https://appassets.androidplatform.net/*" 会使
                    // 真机 chromium 抛 IllegalArgumentException（Robolectric stub 不校验，
                    // 测试全绿但真机崩溃；2026-08-15 真机验证发现）
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        startScript,
                        setOf("https://appassets.androidplatform.net"),
                    )
                }
                addJavascriptInterface(bridge, "AndroidBridge")
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            assetLoader.shouldInterceptRequest(request.url)?.let {
                                return it
                            }
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
            // 内联 CSS：assets 加载失败（AssetLoader 未拦截/缓存）会丢全部背景样式，
            // 内联后根治（2026-08-16 真机验证：alert/代码块/行内代码背景缺失）。
            val inlineCss =
                mapOf(
                    "github-markdown.css" to readAsset(context, "webview/github-markdown.css"),
                    "markdown-you.css" to readAsset(context, "webview/markdown-you.css"),
                    "highlight-theme.css" to readAsset(context, "webview/highlight-theme.css"),
                )
            val html = WebViewHtmlBuilder.build(sanitizedHtml, themeVariables, isDark, renderMode, baseRepoUrl, inlineCss)
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/",
                html,
                "text/html",
                "utf-8",
                null,
            )
            // addDocumentStartJavaScript 注册后脚本固定，重组（如系统深浅色切换）
            // 生成的新 startScript 不会自动生效；必须在此用 evaluateJavascript
            // 重放当前脚本，否则旧 light 脚本会把变量内联钉死导致不随主题切换。
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                webView.evaluateJavascript(startScript, null)
            }
        },
    )
}

/** 读取 assets 文件为字符串（CSS 内联用）。 */
private fun readAsset(
    context: Context,
    path: String,
): String =
    try {
        context.assets
            .open(path)
            .bufferedReader()
            .use { it.readText() }
    } catch (_: Exception) {
        ""
    }
