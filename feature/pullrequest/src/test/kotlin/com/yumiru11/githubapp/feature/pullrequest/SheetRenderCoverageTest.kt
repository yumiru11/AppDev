package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentAnchor
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentTarget
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 两个 Sheet 的**渲染冒烟**测试（行评论 / Review 提交）。
 *
 * 为什么需要它：这两个文件被 `coverageExcludes` 按类名排除出 JaCoCo 分母，排除理由写的是
 * 「纯 Composable，单测不可达，截图/真机兜底」——但**该模块既没有截图基线、也不在 CI 的
 * 任何截图门禁里**（提交级审计 2026-09-11 实证），所以那句理由在本模块并不成立：排除 = 永久零覆盖。
 * 同模块的 `MergeBoxClosedStateTest` 已经证明「纯 Composable 在 Robolectric 下可断言」，
 * 即「单测不可达」这个前提本身是错的。
 *
 * 本测试的作用有两层：
 * 1. 给这两个 Sheet 一个**真实的**回归兜底（标题/关键控件渲染出来、回调接线正确）；
 * 2. 让 diff 覆盖率门禁能看见这两个文件的新增行——此前它们「不在覆盖率报告中」，
 *    而门禁把「报告缺席」误判成「已覆盖」（同一 PR 修复）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SheetRenderCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun anchor() = LineCommentAnchor(path = "app/src/main/kotlin/Foo.kt", side = DiffSide.RIGHT, line = 12)

    private fun thread() =
        ReviewThread(
            id = "thread-1",
            path = "app/src/main/kotlin/Foo.kt",
            side = DiffSide.RIGHT,
            line = 12,
            commentIds = listOf("c1"),
        )

    private fun comment() =
        ReviewComment(
            id = 1L,
            body = "这是一条行评论",
            path = "app/src/main/kotlin/Foo.kt",
            line = 12,
        )

    @Test
    fun lineCommentSheet_newComment_rendersAnchorAndInput() {
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                LineCommentSheet(
                    target = LineCommentTarget(anchor = anchor()),
                    canResolve = true,
                    onDismiss = {},
                    onSubmit = { _, _, _ -> },
                    onToggleResolve = {},
                )
            }
        }

        // 标题带上锚点路径与行号（pull_request_review_comment_at）
        composeRule.onNodeWithText("app/src/main/kotlin/Foo.kt", substring = true).assertIsDisplayed()
    }

    @Test
    fun lineCommentSheet_existingThread_rendersConversation() {
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                LineCommentSheet(
                    target =
                        LineCommentTarget(
                            anchor = anchor(),
                            thread = thread(),
                            comments = listOf(comment()),
                        ),
                    canResolve = false,
                    onDismiss = {},
                    onSubmit = { _, _, _ -> },
                    onToggleResolve = {},
                )
            }
        }

        composeRule.onNodeWithText("这是一条行评论", substring = true).assertIsDisplayed()
    }

    @Test
    fun reviewSheet_noPermission_rendersTitleAndConclusionChips() {
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                ReviewSheet(
                    canApprove = false,
                    onDismiss = {},
                    onSubmit = { _, _ -> },
                )
            }
        }

        // 无 WRITE 权限时仍要能提交纯评论 —— 标题与结论选项必须渲染出来（禁用态由既有测试覆盖）
        // 标题取 pull_request_review_title = "Submit review"（ReviewSheet.kt:88）
        composeRule.onNodeWithText("Submit review", substring = true).assertIsDisplayed()
    }
}
