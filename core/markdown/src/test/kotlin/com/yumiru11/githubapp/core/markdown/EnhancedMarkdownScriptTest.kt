package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `<script>` 元素语义回归（缺陷 #4）：标签与正文都必须从渲染产物中消失。
 *
 * - 行内形态（段落中的 `<script>`）由 [com.yumiru11.githubapp.core.markdown.native.NativeMarkdownPreprocessor]
 *   在渲染前整体删除；
 * - 块级形态（独占一行的 `<script>`）是 HTML_BLOCK，由 [stripHtmlTags] 兜底删除。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnhancedMarkdownScriptTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inlineScript_bodyTextIsNotRendered() {
        render("安全子集之外的标签应被清洗：<script>alert(1)</script>")

        assertTrue("script 正文 alert(1) 不应出现在语义树", nodesWithSubstring("alert(1)").isEmpty())
        assertEquals("script 之外的正文必须保留", 1, nodesWithSubstring("安全子集之外的标签应被清洗：").size)
    }

    @Test
    fun blockScript_bodyTextIsNotRendered() {
        render("<script>\nalert(2)\n</script>")

        assertTrue("块级 script 正文 alert(2) 不应出现在语义树", nodesWithSubstring("alert(2)").isEmpty())
    }

    private fun render(markdown: String) {
        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(markdown = markdown)
            }
        }
        composeRule.waitForIdle()
    }

    private fun nodesWithSubstring(
        @Suppress("SameParameterValue") text: String,
    ) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
}
