package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原生列表组件的语义回归（`EnhancedList.kt`）。
 *
 * 覆盖两条此前无单测的链路（2026-09-14 回滚 #281 时被 diff 覆盖率门禁点名）：
 * - [EnhancedUnorderedList] / [EnhancedOrderedList] 的 marker 渲染（`•` / `1.`）
 * - 嵌套列表的换行缩进分支（`EnhancedList.kt` 的 nestedLists Box，此前只靠截图守）
 *
 * 断言用「marker 文本真实出现」作为**执行证据**：marker 只由这两个组件产出，
 * 因此测试通过即证明对应代码路径被执行（不是「凡测试在跑就算覆盖」）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnhancedListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unorderedList_withNestedItem_rendersBulletsAndNestedContent() {
        render(
            """
            - BULLET_ONE
            - BULLET_TWO
              - NESTED_ITEM
            """.trimIndent(),
        )

        assertEquals("2 个顶层项 + 1 个嵌套项 = 3 个 `•` marker", 3, countNodesWithText("\u2022"))
        assertEquals("嵌套项内容必须渲染（换行缩进分支被执行）", 1, countNodesWithText("NESTED_ITEM"))
        assertEquals("父项内容不受嵌套影响", 1, countNodesWithText("BULLET_ONE"))
    }

    @Test
    fun orderedList_rendersIncrementingMarkers() {
        render(
            """
            1. ORDERED_ONE
            2. ORDERED_TWO
            """.trimIndent(),
        )

        assertEquals("有序 marker 从 1 开始", 1, countNodesWithText("1."))
        assertEquals("有序 marker 递增", 1, countNodesWithText("2."))
        assertEquals("有序项内容渲染", 1, countNodesWithText("ORDERED_TWO"))
    }

    private fun render(markdown: String) {
        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(markdown = markdown)
            }
        }
        composeRule.waitForIdle()
    }

    private fun countNodesWithText(text: String): Int =
        composeRule
            .onAllNodesWithText(text)
            .fetchSemanticsNodes()
            .size
}
