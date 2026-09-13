package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
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
 * [AppFilterChip] 语义断言（UI-2 选中态 / 禁用态 / RTL 方向）。
 *
 * RTL 断言用**几何证据**而非「不崩溃」：带前置图标时，标签中心必须落在 chip 中心
 * 的起始侧（LTR 在其右、RTL 在其左）—— 直接证明组件沿 start/end 布局，
 * 而不是靠「看起来没问题」。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppFilterChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appFilterChip_selected_exposesSelectedSemantics() {
        composeRule.setContent {
            AppTheme { AppFilterChip(selected = true, onClick = {}, label = "Kotlin") }
        }
        composeRule.onNodeWithText("Kotlin").assertIsSelected()
    }

    @Test
    fun appFilterChip_unselected_isNotSelected() {
        composeRule.setContent {
            AppTheme { AppFilterChip(selected = false, onClick = {}, label = "Kotlin") }
        }
        composeRule.onNodeWithText("Kotlin").assertIsNotSelected()
    }

    @Test
    fun appFilterChip_clicked_invokesOnClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme { AppFilterChip(selected = false, onClick = { clicks++ }, label = "Kotlin") }
        }
        composeRule.onNodeWithText("Kotlin").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appFilterChip_disabled_isNotEnabledAndIgnoresClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                AppFilterChip(
                    selected = false,
                    onClick = { clicks++ },
                    label = "Kotlin",
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithText("Kotlin").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    /** i18n：组件不内嵌任何文案，中文标签原样透出（en/zh-rCN 由调用方 stringResource 成对提供）。 */
    @Test
    fun appFilterChip_chineseLabel_rendersCallerProvidedText() {
        composeRule.setContent {
            AppTheme { AppFilterChip(selected = true, onClick = {}, label = "已选中") }
        }
        composeRule.onNodeWithText("已选中").assertIsDisplayed()
    }

    /** LTR：前置图标在左，标签中心落在 chip 中心右侧（start/end 布局的 LTR 形态）。 */
    @Test
    fun appFilterChip_ltrWithLeadingIcon_labelCenterIsAfterChipCenter() {
        composeRule.setContent {
            AppTheme {
                AppFilterChip(
                    selected = true,
                    onClick = {},
                    label = "Label",
                    leadingIcon = AppDevOcticons.Check,
                )
            }
        }
        val labelCenter = composeRule.onNodeWithText("Label", useUnmergedTree = true).getUnclippedBoundsInRoot().centerX()
        val chipCenter = composeRule.onNode(hasClickAction()).getUnclippedBoundsInRoot().centerX()
        assertTrue("LTR 标签中心应 > chip 中心（$labelCenter vs $chipCenter）", labelCenter > chipCenter)
    }

    /** RTL：同一组合镜像后标签中心必须翻到 chip 中心左侧。 */
    @Test
    fun appFilterChip_rtlWithLeadingIcon_labelCenterIsBeforeChipCenter() {
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppFilterChip(
                        selected = true,
                        onClick = {},
                        label = "Label",
                        leadingIcon = AppDevOcticons.Check,
                    )
                }
            }
        }
        val labelCenter = composeRule.onNodeWithText("Label", useUnmergedTree = true).getUnclippedBoundsInRoot().centerX()
        val chipCenter = composeRule.onNode(hasClickAction()).getUnclippedBoundsInRoot().centerX()
        assertTrue("RTL 标签中心应 < chip 中心（$labelCenter vs $chipCenter）", labelCenter < chipCenter)
    }

    @Test
    fun appFilterChip_leadingIcon_isDecorativeWithoutContentDescription() {
        composeRule.setContent {
            AppTheme {
                AppFilterChip(
                    selected = false,
                    onClick = {},
                    label = "Label",
                    leadingIcon = AppDevOcticons.Check,
                )
            }
        }
        // 合并语义树里只有一个可点击 chip 节点；装饰图标没有独立语义节点
        composeRule.onNode(hasClickAction()).assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    /** [DpRect] 水平中心（`DpRect.center` 在 compose-ui 无公开属性，手工取中）。 */
    private fun DpRect.centerX() = (left + right) / 2
}
