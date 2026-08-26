package com.yumiru11.githubapp.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.home.model.RepoOption
import com.yumiru11.githubapp.feature.home.ui.RepoPickerSheetContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [RepoPickerSheetContent] 语义断言（#89）：三态渲染与选中回调。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RepoPickerSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repoPickerContent_ready_showsTitleAndPicksOnRowTap() {
        var picked = ""
        composeRule.setContent {
            AppTheme {
                RepoPickerSheetContent(
                    uiState = RepoPickerUiState.Ready(listOf(RepoOption("octocat", "hello-world", "My first repo", false))),
                    onPick = { owner, repo -> picked = "$owner/$repo" },
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Choose a repository").assertIsDisplayed()
        composeRule.onNodeWithText("octocat/hello-world").assertIsDisplayed()
        composeRule.onNodeWithText("octocat/hello-world").performClick()
        assertEquals("octocat/hello-world", picked)
    }

    @Test
    fun repoPickerContent_readyEmpty_showsEmptyState() {
        composeRule.setContent {
            AppTheme {
                RepoPickerSheetContent(
                    uiState = RepoPickerUiState.Ready(emptyList()),
                    onPick = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("No repositories found").assertIsDisplayed()
    }

    @Test
    fun repoPickerContent_error_showsRetryAndFires() {
        var retries = 0
        composeRule.setContent {
            AppTheme {
                RepoPickerSheetContent(
                    uiState = RepoPickerUiState.Error(errorType = HomeErrorType.NETWORK),
                    onPick = { _, _ -> },
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Couldn't load repositories").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }
}
