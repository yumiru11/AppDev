package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
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
 * [AppDialog] 语义断言：内容槽透出 + 确认/关闭回调接线。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appDialog_titleAndText_areRendered() {
        composeRule.setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    confirmButton = { Text(text = "OK") },
                    title = { Text(text = "About") },
                    text = { Text(text = "Version 1.0") },
                )
            }
        }
        composeRule.onNodeWithText("About").assertIsDisplayed()
        composeRule.onNodeWithText("Version 1.0").assertIsDisplayed()
    }

    @Test
    fun appDialog_confirmButtonClick_invokesItsOwnHandler() {
        var confirmed = 0
        composeRule.setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = { confirmed++ }) { Text(text = "OK") }
                    },
                )
            }
        }
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun appDialog_dismissButtonClick_invokesItsOwnHandler() {
        var dismissed = 0
        composeRule.setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text(text = "OK") } },
                    dismissButton = {
                        TextButton(onClick = { dismissed++ }) { Text(text = "Cancel") }
                    },
                )
            }
        }
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
    }

    /** i18n：标题/正文/按钮全部是内容槽，中文文案由调用方 stringResource 提供。 */
    @Test
    fun appDialog_chineseContent_rendersCallerProvidedText() {
        composeRule.setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text(text = "确定") } },
                    title = { Text(text = "关于") },
                )
            }
        }
        composeRule.onNodeWithText("关于").assertIsDisplayed()
        composeRule.onNodeWithText("确定").assertIsDisplayed()
    }
}
