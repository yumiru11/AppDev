package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
 * [AppChip]（assist 语义薄包装）语义断言。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appChip_defaultParameters_rendersCallerLabel() {
        composeRule.setContent {
            AppTheme { AppChip(onClick = {}, label = { Text(text = "Assist") }) }
        }
        composeRule.onNodeWithText("Assist").assertIsDisplayed()
    }

    @Test
    fun appChip_clicked_invokesOnClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme { AppChip(onClick = { clicks++ }, label = { Text(text = "Assist") }) }
        }
        composeRule.onNodeWithText("Assist").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appChip_disabled_isNotEnabledAndIgnoresClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                AppChip(
                    onClick = { clicks++ },
                    label = { Text(text = "Assist") },
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithText("Assist").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    /** i18n：标签内容完全由调用方提供，中文文案原样渲染。 */
    @Test
    fun appChip_chineseLabel_rendersCallerProvidedText() {
        composeRule.setContent {
            AppTheme { AppChip(onClick = {}, label = { Text(text = "主题") }) }
        }
        composeRule.onNodeWithText("主题").assertIsDisplayed()
    }
}
