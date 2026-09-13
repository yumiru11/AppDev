package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线 KaTeX 数学渲染（post-sanitize pass）的**真实执行回归**。
 *
 * `math-render-harness.js` 用生产 assets 里的真实 `katex.min.js`（0.18.7）与真实
 * `renderer.js`，配最小 fake DOM 端到端跑 `renderMath(root)`——证明的是「哪段文本被选中、
 * 用什么选项渲染、DOM 产出什么、其余内容是否无损」，不是源码文本里有没有某个字符串。
 * 浏览器真实排版（字体栅格化）仍由 CI 真机截图通道兜底。
 *
 * harness 输出为逐行 `[key] value`；本类解析成 map 后断言。node 缺失时经
 * [NodeRendererHarness.requireAvailable] assume 跳过（CI 自带 node）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MathRenderExecutionTest {
    private val output: Map<String, String> by lazy { parse(NodeRendererHarness.runMathChannel()) }

    @Test
    fun mathHarness_realKatexVersion_isPinnedAt0_18_7() {
        assertEquals("生产 assets 必须是 KaTeX 0.18.7（升级需同步体积与兼容性结论）", "0.18.7", output["katexVersion"])
    }

    @Test
    fun scanMathText_delimiterMatrix_matchesDocumentedRules() {
        assertEquals("行内 \$x\$ 必须命中且为非块级", """["x:false"]""", output["scanInline"])
        assertEquals("块级 \$\$…\$\$ 必须命中且可跨行", """["\na+b\n:true"]""", output["scanBlock"])
        assertEquals("价格/变量形态不得命中", "0", output["scanPrice"])
        assertEquals("行内公式不得跨行", "0", output["scanMultilineInline"])
        assertEquals("词边界两侧不得命中", "0,0", output["scanWordBoundary"])
        assertEquals("\$\$\$…\$\$\$ 粘连形态不得命中", "0", output["scanTripleDollar"])
    }

    @Test
    fun renderMath_fixtureTree_rendersOnlyMathLeavingCodePricesAndMultilineIntact() {
        assertEquals("主场景应渲染 5 个公式（含 1 个错误态）", "5", output["rendered"])
        assertEquals("块级 1 + 行内 4", "block,inline,inline,inline,inline", output["dataMath"])
        assertTrue(
            "真实 KaTeX 必须产出排版 DOM（.katex-html），不是只把文本包一层",
            (output["katexHtmlSlots"]?.toIntOrNull() ?: 0) >= 4,
        )
        assertTrue(
            "MathML 节点必须存在（无障碍语义）",
            (output["mathmlNodes"]?.toIntOrNull() ?: 0) > 0,
        )
        assertTrue("行内公式原文必须来自 \$…\$ 内容", output["texValues"]!!.contains("E = mc^2"))
        assertTrue("块级公式原文必须保留换行", output["texValues"]!!.contains("\\int_0^\\infty e^{-x^2} dx"))
        assertEquals("代码块内容不得被数学化", "true", output["codeUntouched"])
        assertEquals("行内代码内容不得被数学化", "true", output["inlineCodeUntouched"])
        assertEquals("\$5/\$10 价格形态不得被数学化", "true", output["priceUntouched"])
        assertEquals("跨行 \$a\\nb\$ 不得被数学化", "true", output["crossLineUntouched"])
    }

    @Test
    fun renderMath_parseError_rendersThemedErrorSpanNotCrash() {
        assertEquals("语法错误必须渲染为 1 个 .katex-error", "1", output["errorSpans"])
        assertEquals(
            "无主题变量时 errorColor 走备用色（深色可读）",
            "color:#b3261e",
            output["errorStyle"],
        )
    }

    @Test
    fun renderMath_trustFalse_neverEmitsAnchors() {
        assertEquals("trust:false 下 \\href 不得产出 <a>", "0", output["trustAnchors"])
    }

    @Test
    fun renderMath_themeVariablePresent_usesErrorColorFromCssVariable() {
        assertEquals(
            "--md-sys-color-error 可用时 errorColor 必须取自该变量（不写死 #cc0000）",
            "color:#ff0000",
            output["themedErrorStyle"],
        )
    }

    @Test
    fun renderMath_formulaBudgetExceeded_keepsRemainderAsPlainText() {
        assertEquals("maxFormulas 上限必须生效", "2", output["cappedRendered"])
        assertTrue(
            "预算外的公式必须原文保留，不得静默丢失",
            output["cappedRemaining"]!!.contains("\$a3\$ \$a4\$ \$a5\$"),
        )
    }

    @Test
    fun renderMath_katexNotInjected_noopLeavingTextIntact() {
        assertEquals("未注入 katex.min.js 时必须 no-op（0 个渲染、原文不动）", "0,true", output["withoutKatex"])
    }

    private fun parse(raw: String): Map<String, String> =
        raw
            .lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("[") || !trimmed.contains("] ")) return@mapNotNull null
                val key = trimmed.substringAfter("[").substringBefore("]")
                key to trimmed.substringAfter("] ")
            }.toMap()
}
