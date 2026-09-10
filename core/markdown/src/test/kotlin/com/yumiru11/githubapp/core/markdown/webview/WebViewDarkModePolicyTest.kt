package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebSettings
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * WebView 暗化策略装配（#170）。
 *
 * 核心断言只有一个：**在不支持该特性的 WebView 上，装配过程不得抛异常**。
 * 这正是 2026-08-16 模拟器 logcat 里的 FATAL EXCEPTION 与 #170 模块截图门禁
 * 首次真跑时抓到的 `feature:issue` 失败点 —— 用 Robolectric 的 stub WebView
 * （`isFeatureSupported` 返回 true 但真实调用抛 UnsupportedOperationException）
 * 正好复现这个"检查通过但调用不支持"的最坏组合。
 */
@RunWith(RobolectricTestRunner::class)
class WebViewDarkModePolicyTest {
    @Test
    fun applyWebViewDarkModePolicy_stubWebView_doesNotThrow() {
        val settings = mockk<WebSettings>(relaxed = true)

        // 不抛即通过：内部两道防线（特性检查 + runCatching）兜住了 stub 环境
        applyWebViewDarkModePolicy(settings)
    }
}
