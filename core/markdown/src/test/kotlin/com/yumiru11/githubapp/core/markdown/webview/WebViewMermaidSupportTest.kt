package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebView
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mermaid 运行时的 Chromium 版本门禁（纯 JVM，可行性报告 §2.5/§6.4）。
 *
 * 要守的契约：
 * 1. **解析来自 UA**：`Chrome/<major>` 必须被正确取出（多段时取最后一次）；
 * 2. **边界恰好在 94**：class static block 自 Chromium 94 起支持，93 → 拒绝、94 → 放行；
 * 3. **未知一律拒绝**（fail-closed）：UA 缺失/畸形/null → 不注入脚本（回退代码块），
 *    绝不「赌一把」把语法级失败的脚本塞进老 WebView；
 * 4. **WebView 探测失败不崩**：UA 读取抛异常时按不支持处理（runCatching 兜底）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class WebViewMermaidSupportTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun chromiumMajorOf_typicalWebViewUserAgent_parsesMajorVersion() {
        val userAgent =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/126.0.6478.71 Mobile Safari/537.36"

        assertEquals(126, WebViewMermaidSupport.chromiumMajorOf(userAgent))
    }

    @Test
    fun chromiumMajorOf_multipleChromeMarkers_takesLastOccurrence() {
        // 某些厂商 UA 会在前缀里带历史的 Chrome/ 标记；末尾的才是真实引擎版本
        val userAgent = "Mozilla/5.0 Chrome/70.0.3538.80 VivoBrowser/14.0 Chrome/122.0.6261.119 Mobile"

        assertEquals(122, WebViewMermaidSupport.chromiumMajorOf(userAgent))
    }

    @Test
    fun chromiumMajorOf_missingOrMalformedUserAgent_returnsNull() {
        assertNull("null UA 必须返回 null", WebViewMermaidSupport.chromiumMajorOf(null))
        assertNull("空 UA 必须返回 null", WebViewMermaidSupport.chromiumMajorOf(""))
        assertNull("无 Chrome 段的 UA 必须返回 null", WebViewMermaidSupport.chromiumMajorOf("Some Browser/1.0 (Android)"))
    }

    @Test
    fun isSupported_thresholdBoundary_exactlyAt94() {
        assertFalse("Chromium 93 不得注入（class static block 语法级失败）", WebViewMermaidSupport.isSupported(93))
        assertTrue("Chromium 94 是最低门槛", WebViewMermaidSupport.isSupported(94))
        assertTrue("新版 Chromium 放行", WebViewMermaidSupport.isSupported(126))
        assertFalse("版本未知按不支持处理（fail-closed）", WebViewMermaidSupport.isSupported(null))
    }

    @Test
    fun isRuntimeSupported_nullWebView_failsClosedWithoutCrash() {
        assertFalse(WebViewMermaidSupport.isRuntimeSupported(null))
    }

    @Test
    fun isRuntimeSupported_userAgentReadThrows_failsClosedWithoutCrash() {
        val webView = mockk<WebView>()
        every { webView.settings } throws IllegalStateException("stub: WebView 未就绪")

        assertFalse(
            "UA 读取抛异常必须按不支持处理（降级为代码块，绝不能崩溃）",
            WebViewMermaidSupport.isRuntimeSupported(webView),
        )
    }

    @Test
    fun isRuntimeSupported_modernUserAgent_supports() {
        val webView = mockk<WebView>()
        val settings = mockk<android.webkit.WebSettings>()
        every { webView.settings } returns settings
        every { settings.userAgentString } returns
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"

        assertTrue(WebViewMermaidSupport.isRuntimeSupported(webView))
    }
}
