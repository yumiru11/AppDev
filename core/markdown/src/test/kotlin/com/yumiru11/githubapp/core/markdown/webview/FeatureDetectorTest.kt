package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun containsMath_inlineFormula_returnsTrue() {
        assertTrue(FeatureDetector.containsMath("Inline \$x^2\$ is supported."))
    }

    @Test
    fun containsMath_singleCharacterFormula_returnsTrue() {
        // 2026-09-13 与 JS 侧对齐收紧规则时顺带修的能力缺口：$x$ 这类单字符公式此前漏检
        assertTrue(FeatureDetector.containsMath("基数 \$x\$ 与 \$y\$"))
    }

    @Test
    fun containsMath_blockFormulaAcrossLines_returnsTrue() {
        assertTrue(FeatureDetector.containsMath("$$\nE = mc^2\n$$"))
    }

    @Test
    fun containsMath_formulaInsideCodeFence_returnsFalse() {
        assertFalse(FeatureDetector.containsMath("```bash\necho \"\$HOME\"\n```"))
    }

    @Test
    fun containsMath_priceLikeDollarText_returnsFalse() {
        assertFalse(FeatureDetector.containsMath("It costs \$5 and \$10 today."))
    }

    @Test
    fun containsMath_inlineFormulaSpanningLines_returnsFalse() {
        // 与 renderer.js 的 MATH_TOKEN_REGEX 同规则：行内公式不得跨行（2026-09-13 收紧）
        assertFalse(FeatureDetector.containsMath("\$a\nb\$"))
    }

    // ── 2026-09-13（离线 Mermaid）：containsMermaid 注入开关 ────────────────

    @Test
    fun containsMermaid_lowercaseFence_returnsTrue() {
        assertTrue(FeatureDetector.containsMermaid("# 架构\n\n```mermaid\ngraph TD\nA-->B\n```"))
    }

    @Test
    fun containsMermaid_uppercaseFence_returnsTrue() {
        assertTrue(FeatureDetector.containsMermaid("```MERMAID\nsequenceDiagram\n  A->>B: Hi\n```"))
    }

    @Test
    fun containsMermaid_fenceWithExtraInfoString_returnsTrue() {
        // ```mermaid 后跟信息串（如 title）也是合法围栏
        assertTrue(FeatureDetector.containsMermaid("```mermaid title=\"架构图\"\ngraph TD\nA-->B\n```"))
    }

    @Test
    fun containsMermaid_languagePrefixWithoutWordBoundary_returnsFalse() {
        // ```mermaidx 不是 mermaid 围栏（\b 词边界不得被当成前缀匹配）
        assertFalse(FeatureDetector.containsMermaid("```mermaidish\nnot a diagram\n```"))
    }

    @Test
    fun containsMermaid_plainTextAndInlineCode_returnsFalse() {
        assertFalse(FeatureDetector.containsMermaid("Use `mermaid` for diagrams, plain text only."))
        assertFalse(FeatureDetector.containsMermaid("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun containsMermaid_mermaidFenceInsideDocumentationFence_returnsTrueButRendererRejects() {
        // 有意不剥离围栏代码块（mermaid 围栏本身就是围栏）：文档里展示 mermaid 语法会多注入
        // 一次脚本，但 renderer.js 按清洗后的真实 DOM 独立严判（language-markdown 不是
        // language-mermaid），不会误渲染。这里锁定「注入开关宁可多开，正确性在 JS 侧」。
        assertTrue(FeatureDetector.containsMermaid("````markdown\n```mermaid\ngraph TD\n```\n````"))
    }
}
