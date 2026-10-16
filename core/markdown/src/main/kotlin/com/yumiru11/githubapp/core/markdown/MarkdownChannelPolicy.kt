package com.yumiru11.githubapp.core.markdown

import com.yumiru11.githubapp.core.markdown.webview.FallbackDecision
import com.yumiru11.githubapp.core.markdown.webview.FeatureDetector

/**
 * 渲染通道裁决（纯函数，可单测）：一篇文档该走原生渲染还是 WebView。
 *
 * 判据分两类：
 * - 服务端 HTML：出现 KaTeX 产物或 Mermaid 容器时原生渲染器无法还原（前者是排版后的复杂
 *   标签树，后者需要 JS 引擎），回退 WebView；超长文档同样回退（原生一次性组合超长树代价不可接受）。
 * - 离线 Markdown：交给 [FeatureDetector] 的既有判定（mermaid 围栏 / 数学 / 重型 HTML / 超长）。
 *
 * 本函数只回答「技术上是否需要 WebView」；「用户是否允许回退」由调用方按
 * [com.yumiru11.githubapp.core.datastore.model.MarkdownRenderMode] 裁决。
 */
object MarkdownChannelPolicy {
    /** 原生渲染的文档长度上限（字符）；超出即回退 WebView。 */
    const val MAX_NATIVE_CHARS: Int = 120_000

    private val KATEX_MARKERS = listOf("katex-html", "class=\"katex\"", "<math")
    private val MERMAID_MARKERS = listOf("class=\"mermaid\"", "language-mermaid", "mermaid-diagram")

    fun requiresWebView(
        content: String,
        isServerHtml: Boolean,
    ): Boolean {
        if (content.length > MAX_NATIVE_CHARS) return true
        val lower = content.lowercase()
        val markers = if (isServerHtml) KATEX_MARKERS + MERMAID_MARKERS else MERMAID_MARKERS
        if (markers.any { lower.contains(it) }) return true
        return !isServerHtml && FeatureDetector.shouldFallback(content) is FallbackDecision.WebView
    }
}
