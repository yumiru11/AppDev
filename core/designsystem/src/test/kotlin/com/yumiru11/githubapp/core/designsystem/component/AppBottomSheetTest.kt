package com.yumiru11.githubapp.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppBottomSheet] 默认值与逃生舱的**行为**断言（设计系统 Batch 4，计划 §3.1⑤ / ADR-0010 决策 3）。
 *
 * 弹层是独立 window（几何结论见 [GlassSheetSurface] KDoc），因此用 `ComponentActivity` 规则
 * 跑真实 [androidx.compose.material3.ModalBottomSheet] 组合路径（同 [GlassSheetSurfaceTest]）。
 *
 * 断言设计成「红色敏感」：若包装层把 `contentColor` 默认值钉死（而不是从 `containerColor`
 * 重算），[appBottomSheet_customContainerColor_contentColorFollowsIt] 必红。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppBottomSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun appBottomSheet_defaultParams_rendersContent() {
        composeRule.setContent {
            AppTheme {
                AppBottomSheet(onDismissRequest = {}) {
                    Text(text = "sheet content")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("sheet content").assertIsDisplayed()
    }

    /**
     * 逃生舱：覆盖 `containerColor` 时内容色必须按 `contentColorFor(containerColor)` 重算
     * ——与 M3 的默认参数表达式同源，而不是钉在弹层默认色上。
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun appBottomSheet_customContainerColor_contentColorFollowsIt() {
        var expected: Color? = null
        var actual: Color? = null
        composeRule.setContent {
            AppTheme {
                val container = MaterialTheme.colorScheme.tertiaryContainer
                expected = MaterialTheme.colorScheme.onTertiaryContainer
                AppBottomSheet(
                    onDismissRequest = {},
                    containerColor = container,
                ) {
                    actual = LocalContentColor.current
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("弹层内容色必须随 containerColor 重算", expected, actual)
    }

    /** `sheetState` 逃生舱必须真的接到 M3 弹层上：传入的 state 在展开后不再处于 Hidden。 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun appBottomSheet_customSheetState_isDrivenToVisible() {
        lateinit var sheetState: SheetState
        composeRule.setContent {
            AppTheme {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                AppBottomSheet(onDismissRequest = {}, sheetState = sheetState) {
                    Text(text = "state-driven content")
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            "传入的 sheetState 应被弹层驱动离开 Hidden（current=${sheetState.currentValue}）",
            sheetState.currentValue != SheetValue.Hidden,
        )
    }

    /** `dragHandle` 逃生舱：null 表示不渲染把手，弹层仍正常打开（M3 同语义）。 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun appBottomSheet_nullDragHandle_stillRendersContent() {
        composeRule.setContent {
            AppTheme {
                AppBottomSheet(onDismissRequest = {}, dragHandle = null) {
                    Text(text = "no handle content")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("no handle content").assertIsDisplayed()
    }
}
