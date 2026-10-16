package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 表达性语言组件（胶囊按钮 / 连接式按钮组 / 列表行 / 提示条 / 加载动效）的语义与形状契约。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppExpressiveComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appButton_click_invokesCallback() {
        var clicked = false
        composeRule.setContent { Themed { AppButton(text = "Confirm", onClick = { clicked = true }) } }
        composeRule.onNodeWithText("Confirm").performClick()
        assertTrue(clicked)
    }

    @Test
    fun appButton_disabled_doesNotInvokeCallback() {
        var clicked = false
        composeRule.setContent { Themed { AppButton(text = "Confirm", onClick = { clicked = true }, enabled = false) } }
        composeRule.onNodeWithText("Confirm").performClick()
        assertTrue(!clicked)
    }

    @Test
    fun appConnectedButtonRow_itemClick_invokesOnlyItsOwnCallback() {
        var starClicks = 0
        var watchClicks = 0
        composeRule.setContent {
            Themed {
                AppConnectedButtonRow(
                    actions =
                        listOf(
                            AppConnectedAction(label = "Star", onClick = { starClicks++ }, selected = true),
                            AppConnectedAction(label = "Watch", onClick = { watchClicks++ }),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Watch").performClick()
        assertEquals(0, starClicks)
        assertEquals(1, watchClicks)
    }

    @Test
    fun appConnectedShape_outerAndInnerIndices_usePillAndTightCorner() {
        val first = appConnectedShape(index = 0, size = 2, height = 48.dp)
        assertEquals(
            RoundedCornerShape(topStart = 24.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 24.dp),
            first,
        )
        val second = appConnectedShape(index = 1, size = 2, height = 48.dp)
        assertEquals(
            RoundedCornerShape(topStart = 12.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 12.dp),
            second,
        )
    }

    @Test
    fun appConnectedShape_singleAction_isFullPill() {
        assertEquals(
            RoundedCornerShape(24.dp),
            appConnectedShape(index = 0, size = 1, height = 48.dp),
        )
    }

    @Test
    fun appListRow_click_invokesCallback() {
        var clicked = false
        composeRule.setContent { Themed { AppListRow(title = "Repository", onClick = { clicked = true }) } }
        composeRule.onNodeWithText("Repository").performClick()
        assertTrue(clicked)
    }

    @Test
    fun appNotice_actionSlot_isClickable() {
        var clicked = false
        composeRule.setContent {
            Themed {
                AppNotice(
                    text = "Guest mode",
                    action = { AppButton(text = "Sign in", onClick = { clicked = true }, fillWidth = false, height = 40.dp) },
                )
            }
        }
        composeRule.onNodeWithText("Sign in").performClick()
        assertTrue(clicked)
    }

    @Test
    fun appWavyProgress_rendersSingleNode() {
        composeRule.setContent { Themed { AppWavyProgress(modifier = Modifier.testTag("wavy")) } }
        composeRule.onNodeWithTag("wavy").assertExists()
    }

    @Test
    fun appShimmerSkeleton_rendersSingleNode() {
        composeRule.setContent { Themed { AppShimmerSkeleton(modifier = Modifier.testTag("skeleton")) } }
        composeRule.onNodeWithTag("skeleton").assertExists()
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MaterialTheme { content() }
    }
}
