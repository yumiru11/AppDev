package com.yumiru11.githubapp.feature.issue

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [IssueErrorContent] 渲染冒烟 + 重试接线（设计系统 Batch 2：迁移到共享 `AppErrorState` 后）。
 *
 * 为什么需要它：`IssueStateUi.kt` 是纯 Composable 文件（错误类型 → 本地化文案 + 重试），
 * 此前无单测触达（JaCoCo 实测 0/13 行）→ diff 覆盖率门禁会把迁移产生的新增行判成 0%
 * 覆盖。同模块的截图/Preview 不产生行覆盖；Robolectric 组合测试才是可断言的兜底
 * （与 `:feature:pullrequest` 的 SheetRenderCoverageTest 同策略）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IssueStateUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun issueErrorContent_networkError_rendersLocalizedMessageAndTriggersRetry() {
        var retries = 0
        composeRule.setContent {
            AppTheme {
                IssueErrorContent(
                    errorType = IssueErrorType.NETWORK,
                    onRetry = { retries++ },
                )
            }
        }
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.issue_error_network))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.issue_retry))
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun issueErrorContent_notFoundError_rendersNotFoundMessage() {
        composeRule.setContent {
            AppTheme {
                IssueErrorContent(
                    errorType = IssueErrorType.NOT_FOUND,
                    onRetry = {},
                )
            }
        }
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.issue_error_not_found))
            .assertIsDisplayed()
    }
}
