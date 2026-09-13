package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线 Mermaid 图表渲染（post-sanitize pass）的**真实执行回归**。
 *
 * `mermaid-render-harness.js` 用生产 assets 里的真实 `mermaid/mermaid.tiny.js`（11.17.2）与
 * 真实 `renderer.js`，配最小 fake DOM 端到端跑 `renderMermaid(root)`——证明的是「哪些节点被
 * 选中、用什么 initialize 配置、DOM 产出什么、失败/门禁路径是否回退为代码块」，不是源码文本
 * 里有没有某个字符串。真实栅格化（浏览器布局/SVG 绘制）由 CI 模拟器截图与真机走查兜底。
 *
 * harness 输出为逐行 `[key] value`；本类解析成 map 后断言。node 缺失时经
 * [NodeRendererHarness.requireAvailable] assume 跳过（CI 自带 node）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MermaidRenderExecutionTest {
    private val output: Map<String, String> by lazy { parse(NodeRendererHarness.runMermaidChannel()) }

    @Test
    fun mermaidBundle_shipsAsSelfContainedIifeWithoutDynamicImports() {
        assertEquals(
            "生产 assets 必须是 Mermaid Tiny 11.17.2（实测 2,555,146 B；升级需同步体积与 Chromium 门禁结论）",
            "2555146",
            output["mermaidBundleBytes"],
        )
        assertEquals("IIFE 单文件不得含动态 import（老 WebView 的 asset loader 解析不了）", "0", output["mermaidDynamicImports"])
        assertEquals("版本横幅必须钉在 11.17.2-tiny", "true", output["mermaidVersionMarker"])
        assertTrue(
            "bundle 必须含 class static block（Chromium ≥94 门禁的语法依据，不得凭空写门禁）",
            (output["mermaidStaticBlocks"]?.toIntOrNull() ?: 0) > 0,
        )
    }

    @Test
    fun mermaidBundle_realEngine_parsesFlowchartAndRejectsNonDiagrams() {
        assertEquals("真实 mermaid.parse 必须接受 flowchart 语法", "flowchart-v2", output["realParseSimpleType"])
        assertEquals(
            "34-mermaid 夹具的图骨架必须能被真实引擎解析（标签清洗走真机路径）",
            "flowchart-v2",
            output["realParseFixtureSkeletonType"],
        )
        assertEquals("非图文本必须被真实引擎拒绝", "rejected", output["realParseInvalid"])
    }

    @Test
    fun renderMermaid_offlineFence_takesOverCodeBlockAndInitializesStrictly() {
        assertEquals("离线 `pre > code.language-mermaid` 必须被接管", "1", output["offlineTookOver"])
        assertEquals("initialize 必须恰好一次", "1", output["offlineInitializeCalls"])
        assertEquals("run 必须收到接管的容器", "1", output["offlineRunNodes"])
        assertEquals("成功路径必须产出 SVG", "1", output["offlineSvgCount"])
        assertEquals("普通代码块不得被误接管", "true", output["offlinePlainCodeIntact"])
        assertEquals(
            "安全档必须是 strict + htmlLabels:false + base 主题 + 不自动启动",
            "startOnLoad=false;securityLevel=strict;htmlLabels=false;theme=base",
            output["initConfig"],
        )
    }

    @Test
    fun renderMermaid_themeVariables_readFromInjectedCssVariables() {
        assertEquals(
            "primaryBorderColor 必须取自 --md-sys-color-primary（不写死 mermaid 默认色）",
            "#112233",
            output["themePrimaryBorder"],
        )
        assertEquals(
            "fontFamily 必须取自 --fontStack-sansSerif（默认 trebuchet ms 在 Android 大概率缺字）",
            "Custom Sans, sans-serif",
            output["themeFontFamily"],
        )
        assertEquals("CSS 变量不可用时走备用色（深浅色都可读）", "#6750a4", output["themeFallbackPrimary"])
    }

    @Test
    fun renderMermaid_serverHtmlHighlightShape_alsoTakesOver() {
        assertEquals("服务端 HTML 的 `div.highlight-source-mermaid > pre` 必须被接管", "1", output["serverTookOver"])
        assertEquals("服务端形态同样必须产出 SVG", "1", output["serverSvgCount"])
    }

    @Test
    fun renderMermaid_noDiagram_detectedPerformsNoEngineWork() {
        assertEquals("无图页面不得接管（检测优先于执行）", "0", output["noDiagramTookOver"])
        assertEquals("无图页面不得 initialize（性能护栏）", "0", output["noDiagramInitCalls"])
        assertEquals("无图页面不得 run（性能护栏）", "0", output["noDiagramRunCalls"])
    }

    @Test
    fun renderMermaid_engineUnavailable_fallsBackToPlainCodeBlock() {
        assertEquals("window.mermaid 缺失必须整体 no-op", "0", output["engineAbsentTookOver"])
        assertEquals("代码块原文必须无损保留", "true", output["engineAbsentPreIntact"])
        assertEquals("探测原因必须是 runtime（脚本未注入/解析失败）", "runtime", output["engineAbsentReason"])
    }

    @Test
    fun renderMermaid_classStaticBlockUnsupported_fallsBackWithoutTouchingDom() {
        assertEquals("语法探针失败（Chromium <94）必须整体 no-op", "0", output["syntaxTookOver"])
        assertEquals("门禁路径不得调用引擎、DOM 无损", "true", output["syntaxPreIntact"])
    }

    @Test
    fun renderMermaid_runFailure_restoresOriginalCodeBlock() {
        assertEquals("接管计数按 schedule 计", "1", output["runFailureTookOver"])
        assertEquals("未产出 SVG 时必须恢复原 <pre> 代码块", "true", output["runFailureCodeRestored"])
        assertEquals("回退后不得残留 .mermaid 容器", "true", output["runFailureHolderGone"])
        assertEquals("run reject 同样恢复代码块", "true", output["runRejectCodeRestored"])
    }

    @Test
    fun renderMermaid_initializeThrows_returnsZeroAndRestoresCodeBlock() {
        assertEquals("initialize 同步异常必须返回 0（绝不半接管）", "0", output["initThrowsTookOver"])
        assertEquals("异常路径必须恢复代码块", "true", output["initThrowsCodeRestored"])
    }

    @Test
    fun renderMermaid_diagramBudget_capsTakesOverAndKeepsRestAsCode() {
        assertEquals("maxDiagrams 上限必须生效", "2", output["budgetTookOver"])
        assertEquals("超出的图保持代码块（原文不静默丢失）", "1", output["budgetRemainingPre"])
        assertEquals("run 只收到预算内的容器", "2", output["budgetRunNodes"])
        assertEquals("默认预算必须是 10（低端机护栏）", """{"maxDiagrams":10}""", output["mermaidLimits"])
    }

    @Test
    fun mermaidSourceOf_shapeMatrix_matchesDocumentedForms() {
        assertEquals("离线形态（code.language-mermaid）必须命中", "true", output["sourceOffline"])
        assertEquals("服务端形态（highlight-source-mermaid > pre）必须命中", "true", output["sourceServer"])
        assertEquals("普通代码块必须不命中", "true", output["sourcePlain"])
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
