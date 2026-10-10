package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** [AppSelect] 胶囊下拉的渲染与选择行为。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppSelectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = listOf("system", "light", "dark")

    @Test
    fun appSelect_rendersSelectedOption() {
        composeRule.setContent {
            AppTheme {
                AppSelect(
                    options = options,
                    selectedOption = "light",
                    onOptionSelected = {},
                    optionLabel = { it },
                )
            }
        }
        composeRule.onNodeWithText("light").assertIsDisplayed()
    }

    @Test
    fun appSelect_tapPill_showsAllOptions() {
        composeRule.setContent {
            AppTheme {
                AppSelect(options = options, selectedOption = "system", onOptionSelected = {}, optionLabel = { it })
            }
        }
        composeRule.onNodeWithText("system").performClick()
        composeRule.onNodeWithText("light").assertIsDisplayed()
        composeRule.onNodeWithText("dark").assertIsDisplayed()
    }

    @Test
    fun appSelect_pickOption_reportsSelection() {
        var picked: String? = null
        composeRule.setContent {
            var selected by remember { mutableStateOf("system") }
            AppTheme {
                AppSelect(
                    options = options,
                    selectedOption = selected,
                    onOptionSelected = {
                        picked = it
                        selected = it
                    },
                    optionLabel = { it },
                )
            }
        }
        composeRule.onNodeWithText("system").performClick()
        composeRule.onNodeWithText("dark").performClick()
        assertEquals("dark", picked)
        composeRule.onNodeWithText("dark").assertIsDisplayed()
    }

    @Test
    fun appSelect_disabled_doesNotOpenMenu() {
        composeRule.setContent {
            AppTheme {
                AppSelect(
                    options = options,
                    selectedOption = "system",
                    onOptionSelected = {},
                    optionLabel = { it },
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithText("system").performClick()
        composeRule.onNodeWithText("light").assertDoesNotExist()
    }
}
