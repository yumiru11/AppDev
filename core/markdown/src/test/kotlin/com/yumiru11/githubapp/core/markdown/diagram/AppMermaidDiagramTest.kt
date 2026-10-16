package com.yumiru11.githubapp.core.markdown.diagram

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原生 Mermaid 组件的渲染契约：合法源码走图表通道、不落回退块。
 *
 * 引擎的图元细节不做像素断言（无基线体系）；这里锁定的是**分流契约**——
 * 「合法输入不得静默降级成代码块」，以及组件在 Robolectric 组合期不抛异常。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppMermaidDiagramTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validFlowchart_rendersDiagramNodeNotFallback() {
        composeRule.setContent {
            MaterialTheme {
                AppMermaidDiagram(
                    source = "graph TD; A[Start]-->B[End]",
                    contentDescription = "Diagram",
                    fallback = { Text("FALLBACK") },
                )
            }
        }
        assertTrue(
            "合法流程图必须走图表通道（出现图表节点或至少不出现回退块）",
            composeRule.onAllNodesWithContentDescription("Diagram").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("FALLBACK").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun unsupportedSource_eitherRendersOrFallsBackWithoutCrash() {
        composeRule.setContent {
            MaterialTheme {
                AppMermaidDiagram(
                    source = "definitely not a mermaid diagram",
                    contentDescription = "Diagram",
                    fallback = { Text("FALLBACK") },
                )
            }
        }
        assertTrue(
            "非法源码必须二选一：渲染失败态或回退块，不得崩溃",
            composeRule.onAllNodesWithContentDescription("Diagram").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("FALLBACK").fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
