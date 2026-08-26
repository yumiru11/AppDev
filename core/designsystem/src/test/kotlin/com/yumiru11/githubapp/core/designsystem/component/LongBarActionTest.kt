package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [LongBarAction] 行为断言（#89）：文案渲染、点击回调、禁用态吞掉点击。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LongBarActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longBarAction_click_firesCallbackOnce() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                LongBarAction(
                    text = "Create issue",
                    icon = AppDevOcticons.IssueOpened,
                    onClick = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("Create issue").assertIsDisplayed()
        composeRule.onNodeWithText("Create issue").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun longBarAction_disabled_swallowsClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                LongBarAction(
                    text = "Create repository",
                    icon = AppDevOcticons.Repo,
                    onClick = { clicks++ },
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithText("Create repository").assertIsDisplayed()
        composeRule.onNodeWithText("Create repository").performClick()
        assertEquals(0, clicks)
    }
}
