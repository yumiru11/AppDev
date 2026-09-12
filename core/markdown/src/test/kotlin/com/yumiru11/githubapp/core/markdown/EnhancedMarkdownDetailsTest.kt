package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `<details>` 折叠渲染的**语义回归**：块正文必须只出现一次（折叠时不可见，展开后恰好一次）。
 *
 * ## 背景（缺陷 #1）
 *
 * GitHub 惯用的 details 写法在 `<summary>` 后留空行：
 * ```
 * <details>
 * <summary>X</summary>
 *
 * body
 *
 * </details>
 * ```
 * GFM 把这段拆成三个块（开标签 HTML_BLOCK / 正文段落 / 闭标签 HTML_BLOCK）。
 * 旧实现在开标签处向 `</details>` 借用区间渲染卡片正文，但外层解析仍会把 body
 * 渲染成常驻段落 → **同一段文字出现两次**（折叠语义失效：收起的正文永远可见）。
 *
 * 本测试断言：折叠态 0 次、展开态恰好 1 次。修复前空白行形态折叠态即为 1 次（红）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnhancedMarkdownDetailsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun details_withBlankLineForm_bodyAppearsExactlyOnce_afterExpand() {
        render(
            """
            <details>
            <summary>SUMMARY_ONCE</summary>

            DETAILS_BODY_ONCE

            </details>
            """.trimIndent(),
        )

        assertEquals("折叠态正文不应可见（正文只允许存在于卡片内）", 0, countBodyNodes())
        expand()
        assertEquals("展开态正文必须恰好出现一次（修复前：卡片内一次 + 外层段落一次 = 2）", 1, countBodyNodes())
    }

    @Test
    fun details_withoutBlankLineForm_bodyAppearsExactlyOnce_afterExpand() {
        render(
            """
            <details>
            <summary>SUMMARY_ONCE</summary>
            DETAILS_BODY_ONCE
            </details>
            """.trimIndent(),
        )

        assertEquals("折叠态正文不应可见", 0, countBodyNodes())
        expand()
        assertEquals("展开态正文必须恰好出现一次", 1, countBodyNodes())
    }

    private fun render(markdown: String) {
        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(markdown = markdown)
            }
        }
        composeRule.waitForIdle()
    }

    private fun expand() {
        composeRule.onNodeWithText("SUMMARY_ONCE").performClick()
        composeRule.waitForIdle()
    }

    private fun countBodyNodes(): Int =
        composeRule
            .onAllNodesWithText("DETAILS_BODY_ONCE")
            .fetchSemanticsNodes()
            .size
}
