package com.yumiru11.githubapp.core.markdown

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 增强渲染链的**接线契约**：markdown 里的 HTML 块与 mermaid 围栏必须真的走到
 * 原生 HTML 渲染器 / 原生图表组件（而不是退回纯文本或崩溃）。
 *
 * 断言只看语义可见性，不做像素比对。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class EnhancedMarkdownViewerRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun htmlBlock_rendersThroughNativeHtmlRenderer() {
        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(
                    markdown = "<div align=\"center\"><h3>Release</h3><p>Body with <b>bold</b> text</p></div>",
                    onInternalLink = {},
                )
            }
        }
        composeRule.onNodeWithText("Release").assertExists()
    }

    /**
     * mermaid 围栏必须走原生图表通道（而不是普通代码块），且引擎拒绝时能回退而不崩。
     *
     * 这里只做组合冒烟：图表画在 Canvas 上、代码块经 Sora 视图渲染，两者都不进语义树，
     * 因此**无法**用语义断言区分「画出了图」与「回退成代码块」——那部分由
     * [com.yumiru11.githubapp.core.markdown.diagram.AppMermaidDiagramTest] 的组件级契约
     * 与真机人工走查覆盖。
     */
    @Test
    fun validMermaidFence_rendersThroughNativeChannelWithoutCrash() {
        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(
                    markdown = FENCE + "mermaid\n" + "graph TD; A-->B" + "\n" + FENCE,
                    onInternalLink = {},
                )
            }
        }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun unsupportedMermaidFence_fallsBackWithoutCrash() {
        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(
                    markdown = FENCE + "mermaid\n" + "this is not a mermaid diagram at all" + "\n" + FENCE,
                    onInternalLink = {},
                )
            }
        }
        composeRule.onRoot().assertExists()
    }

    private companion object {
        const val FENCE = "\u0060\u0060\u0060"
    }
}
