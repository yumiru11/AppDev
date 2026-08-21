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

/** [AppEmptyState]/[AppErrorState]/[AppLoadingState] 语义断言（不建截图基线，#84 决策 Q3） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppStateViewsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appEmptyState_fullConfig_rendersAndTriggersAction() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                AppEmptyState(
                    icon = AppDevOcticons.Repo,
                    title = "Nothing here",
                    message = "Create your first repo",
                    actionLabel = "New repository",
                    onAction = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Create your first repo").assertIsDisplayed()
        composeRule.onNodeWithText("New repository").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appEmptyState_titleOnly_rendersWithoutCrash() {
        composeRule.setContent {
            AppTheme { AppEmptyState(icon = AppDevOcticons.Star, title = "No stars") }
        }
        composeRule.onNodeWithText("No stars").assertIsDisplayed()
    }

    @Test
    fun appErrorState_retryAction_triggersCallback() {
        var retries = 0
        composeRule.setContent {
            AppTheme {
                AppErrorState(
                    title = "Failed to load",
                    message = "Network unavailable",
                    actionLabel = "Retry",
                    onAction = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Failed to load").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun appLoadingState_withLabel_rendersLabel() {
        composeRule.setContent {
            AppTheme { AppLoadingState(label = "Loading…") }
        }
        composeRule.onNodeWithText("Loading…").assertIsDisplayed()
    }

    @Test
    fun appLoadingState_withoutLabel_rendersWithoutCrash() {
        composeRule.setContent {
            AppTheme { AppLoadingState() }
        }
        composeRule.waitForIdle()
    }
}
