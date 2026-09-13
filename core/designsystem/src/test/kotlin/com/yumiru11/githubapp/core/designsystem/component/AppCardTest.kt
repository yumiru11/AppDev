package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasNoClickAction
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
 * [AppCard] 语义断言：默认参数可渲染、可点击重载与容器重载的语义差异。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appCard_defaultParameters_rendersContent() {
        composeRule.setContent {
            AppTheme { AppCard { Text(text = "Card content") } }
        }
        composeRule.onNodeWithText("Card content").assertIsDisplayed()
    }

    @Test
    fun appCard_clickable_onClickFires() {
        var clicks = 0
        composeRule.setContent {
            AppTheme { AppCard(onClick = { clicks++ }) { Text(text = "Clickable card") } }
        }
        composeRule.onNodeWithText("Clickable card").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appCard_noOnClick_hasNoClickAction() {
        composeRule.setContent {
            AppTheme { AppCard { Text(text = "Static card") } }
        }
        composeRule.onNodeWithText("Static card").assertHasNoClickAction()
    }
}
