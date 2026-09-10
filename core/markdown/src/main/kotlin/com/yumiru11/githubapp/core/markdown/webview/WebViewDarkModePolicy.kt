@file:Suppress("DEPRECATION") // WebSettingsCompat 的暗化 API 上游已 deprecated，但「关算法暗化 + 保留 web-theme 策略」是已拍板的防双重变暗策略

package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * WebView 暗化策略装配（从 WebViewMarkdownRenderer 的 AndroidView factory 抽出来，issue #170）。
 *
 * **为什么要抽**：这段逻辑原来内联在 Composable 的 factory lambda 里，没有单测可打点，
 * 而它恰好是"真机崩溃雷"的高发区；抽成 internal 顶层函数后可以直接用 Robolectric 断言。
 *
 * **两道防线**（缺一不可，都是实测踩出来的）：
 * 1. **特性检查**：API 30 模拟器 / 旧 WebView APK 不支持 AlgorithmicDarkening，
 *    直接调用抛 `UnsupportedOperationException` 崩溃（2026-08-16 模拟器 logcat 实证）。
 *    `setForceDarkStrategy` 与之同源受限，此前**完全没做检查**（#170 补上）。
 * 2. **异常兜底**：`WebViewFeature.isFeatureSupported` 只按 WebView APK 版本判定，
 *    在 Robolectric / 部分 stub WebView 上会返回 true，而真正的 WebSettingsCompat 调用
 *    仍抛 `UnsupportedOperationException`（androidx.webkit 的已知行为；实测栈为
 *    `WebViewFeatureInternal.getUnsupportedOperationException`）。
 *
 * 降级代价只是少一层暗化策略（页面 CSS 本就是主题驱动出图，见 markdown-you.css），
 * 绝不能因此崩溃。
 */
internal fun applyWebViewDarkModePolicy(settings: WebSettings) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false) }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
        runCatching {
            WebSettingsCompat.setForceDarkStrategy(
                settings,
                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY,
            )
        }
    }
}
