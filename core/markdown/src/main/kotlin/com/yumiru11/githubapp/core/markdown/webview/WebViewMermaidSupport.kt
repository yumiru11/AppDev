package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebView

/**
 * Mermaid 运行时的 WebView 能力門禁（可行性报告 `docs/research/katex-mermaid-offline-feasibility.md` §2.5/§6.4）。
 *
 * **为什么必须门禁**：`mermaid.tiny.js`（11.17.2）内含 ES2024 class static block
 * （实测 513 处 `static{`），Chromium < 94 在**解析期**就拒绝整个脚本——那是脚本内
 * `try/catch` 捕不到的语法级失败。因此 Kotlin 侧先按 WebView UA 的 `Chrome/<major>`
 * 版本决定**是否注入**脚本（不注入 = 零成本回退为普通代码块）；WebView 内的
 * `renderer.js` 仍有独立的语法探针 + `window.mermaid` 检查做第二层门禁。
 *
 * **为什么用 UA 而不是 WebView 包版本**：`WebViewCompat.getCurrentWebViewPackage` 的
 * `versionName` 在部分厂商 WebView 上不是 Chromium 版本；UA 里的 `Chrome/<major>` 是
 * 与渲染引擎直接对应的版本号。解析失败/未知一律按**不支持**处理（最保守）。
 *
 * 纯函数 + 可注入参数（同 `WebViewDarkModePolicy` 的做法），纯 JVM 可测。
 */
internal object WebViewMermaidSupport {
    /** Mermaid 11 构建所需的最低 Chromium 主版本（class static block 自 Chromium 94 起支持）。 */
    const val MIN_CHROMIUM_MAJOR = 94

    /**
     * UA 里的 Chrome 版本段。取**最后一次**命中：部分 WebView UA 形如
     * `Mozilla/5.0 … Version/4.0 Chrome/126.0.6478.71 Mobile Safari/537.36`，
     * 而某些嵌入式 UA 会在前面出现历史的 `Chrome/` 标记。
     */
    private val CHROME_VERSION_REGEX = Regex("""Chrome/(\d+)""")

    /** 从 WebView UA 解析 Chromium 主版本号；无法解析返回 null。 */
    fun chromiumMajorOf(userAgent: String?): Int? =
        CHROME_VERSION_REGEX
            .findAll(userAgent.orEmpty())
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    /** 主版本号是否满足 Mermaid 运行时门槛（null = 版本未知 → 不支持，最保守）。 */
    fun isSupported(chromiumMajor: Int?): Boolean = chromiumMajor != null && chromiumMajor >= MIN_CHROMIUM_MAJOR

    /**
     * 真实 WebView 的探测入口（`WebViewMarkdownRenderer` 使用）。
     *
     * UA 读取本身可能抛（WebView 未就绪/判为 stub 环境），失败一律按不支持处理——
     * 降级代价只是图表回退为代码块，绝不能因此崩溃。
     */
    fun isRuntimeSupported(webView: WebView?): Boolean =
        runCatching { isSupported(chromiumMajorOf(webView?.settings?.userAgentString)) }.getOrDefault(false)
}
