package com.yumiru11.githubapp.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 搜索结果四类行卡片的渲染/点击断言（`SearchResultRows.kt`）。
 *
 * 为什么需要：这四条行卡片是 `Card(` → `AppCard(` 迁移点，而既有截图夹具只覆盖
 * 仓库 Tab（RepositoryRow）—— 其余三行在 diff coverage 门禁里是「新增未覆盖行」。
 * 本测试用四类真实模型逐行渲染，把迁移后的可执行行全部纳入覆盖。
 *
 * `avatarUrl = null`：UserRow 的 `AsyncImage` 不发真实网络请求（离线确定性）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SearchResultRowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repositoryRow_clicked_showsFullNameAndInvokesOnClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                RepositoryRow(
                    repository = GitHubFakes.fakeRepository(ownerLogin = "octocat", name = "Hello-World"),
                    onClick = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("octocat/Hello-World").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun userRow_rendersLoginAndName() {
        composeRule.setContent {
            AppTheme {
                UserRow(
                    user = GitHubFakes.fakeUser(login = "octocat", name = "The Octocat", avatarUrl = null),
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithText("octocat").assertIsDisplayed()
        composeRule.onNodeWithText("The Octocat").assertIsDisplayed()
    }

    @Test
    fun issueRow_rendersTitleAndRepositoryMeta() {
        composeRule.setContent {
            AppTheme {
                IssueRow(
                    issue =
                        SearchIssue(
                            id = 1L,
                            number = 42,
                            title = "Crash on startup",
                            state = "open",
                            isPullRequest = false,
                            repoFullName = "octocat/Hello-World",
                            htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
                        ),
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Crash on startup").assertIsDisplayed()
        composeRule.onNodeWithText("octocat/Hello-World · #42").assertIsDisplayed()
    }

    @Test
    fun codeRow_rendersFileNameAndPath() {
        composeRule.setContent {
            AppTheme {
                CodeRow(
                    item =
                        SearchCodeItem(
                            name = "Main.kt",
                            path = "app/src/main/Main.kt",
                            repoFullName = "octocat/Hello-World",
                        ),
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Main.kt").assertIsDisplayed()
        composeRule.onNodeWithText("app/src/main/Main.kt").assertIsDisplayed()
    }
}
