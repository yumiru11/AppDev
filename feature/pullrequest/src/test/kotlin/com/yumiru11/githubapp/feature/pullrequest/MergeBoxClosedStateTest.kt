package com.yumiru11.githubapp.feature.pullrequest

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MergeBox 关闭态行为断言（#163 L03）。
 *
 * 覆盖：PR 打开 → 合并按钮可点；PR 关闭 → MergeBox 仍展示但合并按钮 disabled（GitHub 行为），
 * 且关闭态不提供 Update branch / 删除分支入口。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MergeBoxClosedStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun actions(
        state: PullRequestState,
        canMerge: Boolean,
    ): ConversationActions =
        ConversationActions(
            state = state,
            mergeableState = MergeableState.MERGEABLE,
            canReview = true,
            canMerge = canMerge,
            canDeleteHeadBranch = false,
            headSameRepo = true,
            canMergeBox = true,
            prTitle = "Add feature",
        )

    @Test
    fun mergeBox_prOpen_mergeButtonEnabled() {
        composeRule.setContent {
            AppTheme {
                PullRequestActionItems(actions = actions(PullRequestState.OPEN, canMerge = true))
            }
        }

        composeRule
            .onNode(hasText(context.getString(R.string.pull_request_merge_box_title)) and hasClickAction())
            .assertIsEnabled()
        composeRule
            .onNodeWithText(context.getString(R.string.pull_request_merge_update_branch))
            .assertIsDisplayed()
    }

    @Test
    fun mergeBox_prClosed_mergeButtonDisabledWithClosedHint() {
        composeRule.setContent {
            AppTheme {
                PullRequestActionItems(actions = actions(PullRequestState.CLOSED, canMerge = false))
            }
        }

        composeRule
            .onNode(hasText(context.getString(R.string.pull_request_merge_box_title)) and hasClickAction())
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(context.getString(R.string.pull_request_merge_closed_hint))
            .assertIsDisplayed()
        // 关闭态不提供 Update branch（分支更新仅对打开态有意义）
        composeRule
            .onNodeWithText(context.getString(R.string.pull_request_merge_update_branch))
            .assertDoesNotExist()
    }

    @Test
    fun mergeBox_mergedPr_hidesMergeBox() {
        composeRule.setContent {
            AppTheme {
                PullRequestActionItems(
                    actions =
                        actions(PullRequestState.MERGED, canMerge = false).copy(canMergeBox = false),
                )
            }
        }

        composeRule
            .onNode(hasText(context.getString(R.string.pull_request_merge_box_title)) and hasClickAction())
            .assertDoesNotExist()
    }
}
