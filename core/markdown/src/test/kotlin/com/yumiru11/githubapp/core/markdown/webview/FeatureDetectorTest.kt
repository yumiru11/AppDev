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
    fun repeatedTables_nowRenderedByNative() {
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

        // 2026-08-16 原型真机验证：EnhancedMarkdownTable 已能渲染表格，不再兜底
        assertTrue(decision is FallbackDecision.Native)
    }

    @Test
    fun detailsNowRenderedByNative() {
        val markdown =
            """
            <details>
              <summary>Click to expand</summary>
              Hidden content here.
            </details>
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        // 2026-08-16 原型真机验证：NativeDetailsCard 已能渲染折叠，不再兜底
        assertTrue(decision is FallbackDecision.Native)
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

    // P1（#64）：MATH 误报修复——代码围栏内的 ${var}/$counter 不是数学公式（2026-08-17 真机
    // 实证：mikepenz README 的代码块字符串模板导致误判 WebView）
    @Test
    fun shouldFallback_mathLookalikeInCodeFence_notMisdetectedAsWebView() {
        val markdown =
            """
            # Releases

            ```kotlin
            val version = "\${'$'}{version}"
            val counter = "\${'$'}counter"
            println("\${'$'}it")
            ```

            ## Usage

            ```bash
            echo "${'$'}HOME"
            ```
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertEquals(
            "代码围栏内的 ${'$'}{var} / ${'$'}counter 不得误判数学公式",
            FallbackDecision.Native,
            decision,
        )
    }

    @Test
    fun shouldFallback_blockMathFormula_returnsWebView() {
        val markdown = "# Formula\n\n\$\$\nE = mc^2\n\$\$"

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue("块级数学公式必须触发 WebView", decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.MATH, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_inlineMathFormula_returnsWebView() {
        val markdown = "Inline math \$x^2\$ is supported."

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue("行内数学公式必须触发 WebView", decision is FallbackDecision.WebView)
        assertEquals(FallbackReason.MATH, (decision as FallbackDecision.WebView).reason)
    }

    @Test
    fun shouldFallback_codeFenceAndRealFormula_mixedStillDetectsMath() {
        val markdown =
            """
            ```kotlin
            val a = "${'$'}{value}"
            ```
            Real formula: ${'$'}${'$'}y = ax + b${'$'}${'$'}
            """.trimIndent()

        val decision = FeatureDetector.shouldFallback(markdown)

        assertTrue("代码围栏外的真实公式仍须触发 WebView", decision is FallbackDecision.WebView)
    }
}
