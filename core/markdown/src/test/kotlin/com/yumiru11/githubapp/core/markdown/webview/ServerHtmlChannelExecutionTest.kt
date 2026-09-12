package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务端 HTML 主通道（`SERVER_HTML`）的**可执行回归**。
 *
 * ## 为什么需要它
 *
 * README 一级通道走 GitHub 服务端 HTML，`renderer.js` 的 `init()` 是这一通道唯一的
 * 行为注入点。2026-09-12 审计发现两个只在这条通道上发生的缺口：代码块从不被高亮、
 * 图片没有懒加载。它们都是 DOM 阶段行为，源码文本断言证明不了——本类用 Node 驱动
 * **真实 `renderer.js` 的 `init()`**（fake DOM + 记录型 hljs stub），断言可观察结果。
 *
 * 不覆盖：真实 highlight.js 解析（需要真实 DOM，由 CI 模拟器截图兜底）、WebView 像素。
 * node 不可用时整类由 `assume` 跳过（见 [NodeRendererHarness.requireAvailable]）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class ServerHtmlChannelExecutionTest {
    @Test
    fun serverHtmlPath_codeBlocksUnderBudget_allHighlighted() {
        val output = NodeRendererHarness.runServerHtmlChannel(blockCount = 3, blockChars = 100)

        assertEquals("预算内代码块必须逐个高亮（主通道此前 0 高亮）", "c0,c1,c2", value(output, "highlighted"))
        assertEquals("3", value(output, "totalBlocks"))
    }

    @Test
    fun serverHtmlPath_blocksOverCap_excessLeftUnhighlighted() {
        val output = NodeRendererHarness.runServerHtmlChannel(blockCount = 33, blockChars = 100)

        val maxBlocks = value(output, "maxBlocks").toInt()
        val highlighted = value(output, "highlighted").split(',').filter { it.isNotBlank() }
        assertEquals("恰好高亮预算内的块数", maxBlocks, highlighted.size)
        assertEquals("按文档顺序取前 maxBlocks 块", (0 until maxBlocks).map { "c$it" }, highlighted)
        assertFalse("超出块预算的块必须保持未高亮（否则护栏形同虚设）", highlighted.contains("c$maxBlocks"))
    }

    @Test
    fun serverHtmlPath_singleBlockOverCharBudget_isSkipped() {
        // 单块 20 万字符 > maxTotalChars（12 万）：整体跳过，不阻塞首屏
        val output = NodeRendererHarness.runServerHtmlChannel(blockCount = 1, blockChars = 200_000)

        assertEquals("超字符预算的单块不得被高亮", "", value(output, "highlighted"))
    }

    @Test
    fun serverHtmlPath_images_getLazyLoadingAndAsyncDecoding() {
        val output = NodeRendererHarness.runServerHtmlChannel(blockCount = 1, blockChars = 10)

        assertTrue(
            "服务端 HTML 的图片必须补 loading=lazy：实际 ${value(output, "image0")}",
            value(output, "image0").contains("loading=lazy"),
        )
        assertTrue("并补 decoding=async", value(output, "image0").contains("decoding=async"))
    }

    @Test
    fun serverHtmlPath_existingLoadingAttribute_isNotOverwritten() {
        val output = NodeRendererHarness.runServerHtmlChannel(blockCount = 1, blockChars = 10)

        assertTrue("服务端显式给出的 loading=eager 不得被改写", value(output, "image1").contains("loading=eager"))
        assertTrue("缺失的 decoding 仍要补 async", value(output, "image1").contains("decoding=async"))
    }

    private fun value(
        output: String,
        key: String,
    ): String =
        output
            .lineSequence()
            .firstOrNull { it.startsWith("[$key]") }
            ?.substringAfter("] ")
            ?.trim()
            ?: error("harness 输出缺少 [$key]:\n$output")
}
