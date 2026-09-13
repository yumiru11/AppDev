package com.yumiru11.githubapp.feature.pullrequest

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PullRequestErrorContent] 渲染冒烟 + 重试接线（设计系统 Batch 2：迁移到共享 `AppErrorState` 后）。
 *
 * 为什么需要它：`PullRequestStateUi.kt` 是纯 Composable 文件（错误类型 → 本地化文案 +
 * 重试），此前无单测触达（JaCoCo 实测 0/13 行）→ diff 覆盖率门禁会把迁移产生的新增行
 * 判成 0% 覆盖。Robolectric 组合测试是可断言的兜底（同模块 SheetRenderCoverageTest 先例）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PullRequestStateUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullRequestErrorContent_networkError_rendersLocalizedMessageAndTriggersRetry() {
        var retries = 0
        composeRule.setContent {
            AppTheme {
                PullRequestErrorContent(
                    errorType = PullRequestErrorType.NETWORK,
                    onRetry = { retries++ },
                )
            }
        }
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.pull_request_error_network))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.pull_request_retry))
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun pullRequestErrorContent_notFoundError_rendersNotFoundMessage() {
        composeRule.setContent {
            AppTheme {
                PullRequestErrorContent(
                    errorType = PullRequestErrorType.NOT_FOUND,
                    onRetry = {},
                )
            }
        }
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.pull_request_error_not_found))
            .assertIsDisplayed()
    }
}
