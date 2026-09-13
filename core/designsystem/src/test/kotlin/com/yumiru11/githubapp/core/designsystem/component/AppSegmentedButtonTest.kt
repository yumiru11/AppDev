package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppSegmentedButton] 语义断言（UI-2 单选态 / 行作用域 / 禁用态）。
 *
 * M3 `SegmentedButton` 的选中语义是 **radio 语义**（`Role.RadioButton` + `Selected`），
 * 与 `TabRow` 的 tab 语义不同（AGENTS.md 方法学 5），本测试同时钉死角色与选中态。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppSegmentedButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appSegmentedButton_selected_exposesRadioButtonSelectedSemantics() {
        composeRule.setContent {
            AppTheme {
                SingleChoiceSegmentedButtonRow {
                    AppSegmentedButton(selected = true, onClick = {}, label = "Unified")
                }
            }
        }
        composeRule
            .onNodeWithText("Unified")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun appSegmentedButton_unselected_isNotSelected() {
        composeRule.setContent {
            AppTheme {
                SingleChoiceSegmentedButtonRow {
                    AppSegmentedButton(selected = false, onClick = {}, label = "Side by side")
                }
            }
        }
        composeRule.onNodeWithText("Side by side").assertIsNotSelected()
    }

    @Test
    fun appSegmentedButton_clicked_invokesOnClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                SingleChoiceSegmentedButtonRow {
                    AppSegmentedButton(selected = false, onClick = { clicks++ }, label = "Unified")
                }
            }
        }
        composeRule.onNodeWithText("Unified").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appSegmentedButton_disabled_isNotEnabledAndIgnoresClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                SingleChoiceSegmentedButtonRow {
                    AppSegmentedButton(
                        selected = false,
                        onClick = { clicks++ },
                        label = "Unified",
                        enabled = false,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Unified").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    /** i18n：按钮文案由调用方提供，中文标签原样透出。 */
    @Test
    fun appSegmentedButton_chineseLabel_rendersCallerProvidedText() {
        composeRule.setContent {
            AppTheme {
                SingleChoiceSegmentedButtonRow {
                    AppSegmentedButton(selected = true, onClick = {}, label = "并排")
                }
            }
        }
        composeRule.onNodeWithText("并排").assertIsDisplayed()
    }
}
