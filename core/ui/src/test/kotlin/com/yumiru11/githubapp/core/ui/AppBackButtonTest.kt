package com.yumiru11.githubapp.core.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [AppBackButton] 语义：可点 + 带无障碍名（返回）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppBackButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appBackButton_click_invokesCallback() {
        var clicks = 0
        composeRule.setContent { AppTheme { AppBackButton(onClick = { clicks++ }) } }
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appBackButton_hasClickAction() {
        composeRule.setContent { AppTheme { AppBackButton(onClick = {}) } }
        composeRule.onNodeWithContentDescription("Back").assertHasClickAction()
    }
}
