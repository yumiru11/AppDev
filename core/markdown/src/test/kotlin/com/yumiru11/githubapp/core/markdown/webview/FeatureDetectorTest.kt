package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FeatureDetector 单元测试（纯函数，JVM 免 Robolectric）。
 *
 * 验证复杂内容探测矩阵：mermaid 围栏 / 重型 HTML / 超长文档 / 普通内容。
 * 命名遵循 methodName_scenario_expectedBehavior 约定。
 */
class FeatureDetectorTest {
    @Test
    fun shouldFallback_plainMarkdown_returnsNative() {
        val markdown = "# Hello\n\nThis is a **plain** README with [a link](https://example.com)."

        val decision = FeatureDetector.shouldFallback(markdown)

        assertEquals(FallbackDecision.Native, decision)
    }

    @Test
    fun shouldFallback_mermaidFence_returnsWebView() {
        val markdown =
            """
            # Architecture

            ```mermaid
            graph LR
                A-->B
                B-->C
            ```
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue("mermaid fence must trigger WebView fallback", decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.MERMAID, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_mermaidFenceUppercase_returnsWebView() {
        val markdown = "```MERMAID\nsequenceDiagram\n  A->>B: Hi\n```"

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue(decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.MERMAID, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_heavyHtmlTable_returnsWebView() {
        val markdown =
            """
            # Comparison

            <table>
              <tr><th>A</th><th>B</th></tr>
              <tr><td>1</td><td>2</td></tr>
            </table>

            <table>
              <tr><th>C</th><th>D</th></tr>
              <tr><td>3</td><td>4</td></tr>
            </table>
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue(decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.HEAVY_HTML, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_detailsSummary_returnsWebView() {
        val markdown =
            """
            <details>
              <summary>Click to expand</summary>
              Hidden content here.
            </details>
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue(decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.HEAVY_HTML, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_svgInline_returnsWebView() {
        val markdown =
            """
            <svg width="100" height="100">
              <circle cx="50" cy="50" r="40"/>
            </svg>
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue(decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.HEAVY_HTML, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_overlyLongDocument_returnsWebView() {
        val markdown =
            buildString {
                repeat(2200) { appendLine("line $it of a very long README.") }
            }

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue(decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.TOO_LONG, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_overlyLargeDocument_returnsWebView() {
        val markdown = "a".repeat(55_000)

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue(decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.TOO_LONG, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_mixedCodeFence_returnsNative() {
        // 单纯代码块不触发兜底（renderer 0.38 支持 codeFence）
        val markdown =
            """
            # Demo

            ```kotlin
            val x = 42
            ```

            Inline `code` and a paragraph.
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertEquals(FallbackDecision.Native, decision)
    }

    @Test
    fun shouldFallback_emptyString_returnsNative() {
        val decision = FeatureDetector.shouldFallback("")

        assertEquals(FallbackDecision.Native, decision)
    }

    @Test
    fun shouldFallback_singleTable_returnsNative() {
        // 单个 markdown 表格由原生 renderer 渲染（ADR-0005：原生裁剪接受）
        val markdown =
            """
            | A | B |
            |---|---|
            | 1 | 2 |
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertEquals(FallbackDecision.Native, decision)
    }
}
