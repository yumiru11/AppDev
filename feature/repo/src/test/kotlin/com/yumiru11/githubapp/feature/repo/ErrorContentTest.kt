package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ErrorContent] 的文案 + Retry 语义测试（#201 P0：错误映射错 + 无效 Retry）。
 *
 * 锁住的三条验收（ticket #201）：
 * - 路径/文件 404 → 「文件不存在」类文案（**不是** Repository not found），且**不给 Retry**
 * - 真·仓库不存在 → 仍是仓库级「仓库未找到」，同样不给 Retry（确定性失败）
 * - 网络错误 → 保留可重试入口
 *
 * 文案一律按 `stringResource` 解析后断言（en/zh-rCN 成对新增由 I18nParityTest 守卫）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ErrorContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(errorType: RepoErrorType) {
        composeRule.setContent {
            MaterialTheme {
                ErrorContent(errorType = errorType, onRetry = {})
            }
        }
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    @Test
    fun errorContent_pathNotFound_showsFileCopyAndNoRetry() {
        render(RepoErrorType.PATH_NOT_FOUND)

        composeRule.onNodeWithText(string(R.string.repo_error_path_not_found)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.repo_error_not_found)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.repo_retry)).assertCountEquals(0)
    }

    @Test
    fun errorContent_repositoryNotFound_showsRepositoryCopyAndNoRetry() {
        render(RepoErrorType.NOT_FOUND)

        composeRule.onNodeWithText(string(R.string.repo_error_not_found)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.repo_error_path_not_found)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.repo_retry)).assertCountEquals(0)
    }

    @Test
    fun errorContent_network_showsRetry() {
        render(RepoErrorType.NETWORK)

        composeRule.onNodeWithText(string(R.string.repo_error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.repo_retry)).assertIsDisplayed()
    }

    @Test
    fun errorContent_forbidden_showsRetry() {
        render(RepoErrorType.FORBIDDEN)

        composeRule.onNodeWithText(string(R.string.repo_error_forbidden)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.repo_retry)).assertIsDisplayed()
    }
}
