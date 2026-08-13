package com.yumiru11.githubapp.core.markdown.webview

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * WebView 安全锁（plan.md §2.14 红线）。
 *
 * 配置 WebSettings 全锁：禁 file/dom/geo/universal access，仅保留 JavaScript（必需，
 * 用于跑 DOMPurify 清洗 + JS bridge 回调）。
 *
 * 调用方应在 WebView 创建后立即调用 [apply]，确保安全配置先于任何 loadData/loadUrl。
 */
object WebViewSecurity {
    /**
     * 应用安全锁配置到 WebView。
     *
     * 红线：
     * - `javaScriptEnabled = true`（必需：DOMPurify + bridge）
     * - `allowFileAccess / allowContentAccess = false`
     * - `allowFileAccessFromFileURLs / allowUniversalAccessFromFileURLs = false`
     * - `domStorageEnabled = false`（localStorage 可能残留敏感数据，禁）
     * - `databaseEnabled / geolocationEnabled = false`
     * - `mediaPlaybackRequiresUserGesture = true`（防自动播放）
     *
     * @SuppressLint("SetJavaScriptEnabled")：JS 启用是设计需求（plan.md §2.9 兜底渲染依赖），
     * 已通过 DOMPurify + HtmlSanitizer + bridge 白名单三重防护。
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION") // 显式禁用已废弃的危险访问开关（file/dom/universal access），废弃不等于可启用
    fun apply(webView: WebView) {
        val settings: WebSettings = webView.settings
        // 必需：DOMPurify 清洗 + JS bridge 回调
        settings.javaScriptEnabled = true
        // 文件 / 内容访问全锁
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        // 存储 / 定位全锁
        settings.domStorageEnabled = false
        settings.databaseEnabled = false
        settings.setGeolocationEnabled(false)
        // 缓存控制：默认走 WebViewAssetLoader，禁文件协议缓存
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        // 媒体自动播放禁
        settings.mediaPlaybackRequiresUserGesture = true
        // 缩放禁（移动端由容器处理手势）
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        // 移除遗留的 JS 接口（旧 Android WebView 注入的搜索桥/无障碍桥，安全风险）
        webView.removeJavascriptInterface("searchBoxJavaBridge_")
        webView.removeJavascriptInterface("accessibility")
        webView.removeJavascriptInterface("accessibilityTraversal")
    }
}
