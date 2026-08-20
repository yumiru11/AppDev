package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueComment
import com.yumiru11.githubapp.feature.issue.model.IssueReaction
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * IssueDetailViewModel 写操作单测（T14，MockK + Turbine）。
 *
 * 覆盖：关闭/重开乐观更新与失败回滚、评论增改删乐观插入/替换/回滚、
 * 反应 toggle（计数 + myReactions 跟踪）、任务列表 checkbox 反向同步与回滚、
 * viewerPermission 权限门控（canEditIssue/canCloseReopen/canComment/canEditComment）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IssueDetailViewModelWriteTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val owner = "octocat"
    private val repo = "Hello-World"
    private val number = 42

    private fun issue(state: IssueState = IssueState.OPEN): Issue =
        Issue(
            id = 1L,
            number = number,
            title = "Bug report",
            state = state,
            body = "- [ ] task",
            author = IssueUser(login = "octocat"),
        )

    private fun writeContext(permission: IssueViewerPermission = IssueViewerPermission.WRITE): IssueWriteContext =
        IssueWriteContext(viewerLogin = "octocat", viewerPermission = permission, issueNodeId = "I_kwDOA")

    private fun repository(
        issue: Issue = issue(),
        timeline: List<IssueTimelineItem> = emptyList(),
        context: IssueWriteContext = writeContext(),
    ): IssueRepository =
        mockk<IssueRepository> {
            coEvery { getIssue(owner, repo, number) } returns issue
            coEvery { timeline(owner, repo, number) } returns timeline
            coEvery { getIssueWriteContext(owner, repo, number) } returns context
        }

    private fun viewModel(repository: IssueRepository): IssueDetailViewModel =
        IssueDetailViewModel(
            SavedStateHandle(mapOf("owner" to owner, "repo" to repo, "number" to number)),
            repository,
        )

    private fun successState(viewModel: IssueDetailViewModel): IssueDetailUiState.Success =
        viewModel.uiState.value as IssueDetailUiState.Success

    // ---- 关闭/重开 ----

    @Test
    fun closeIssue_success_optimisticThenServerState_emitsClosedSnackbar() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.updateIssue(owner, repo, number, state = "closed") } returns issue(IssueState.CLOSED)
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.closeIssue()
                runCurrent()
                // 乐观更新：StateChip 立即变 CLOSED
                assertEquals(IssueState.CLOSED, successState(vm).issue.state)
                advanceUntilIdle()
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ISSUE_CLOSED), awaitItem())
                assertEquals(IssueState.CLOSED, successState(vm).issue.state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun closeIssue_failure_rollsBackState_emitsErrorSnackbar() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.updateIssue(any(), any(), any(), state = "closed") } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.closeIssue()
                advanceUntilIdle()
                // 失败回滚：StateChip 恢复 OPEN
                assertEquals(IssueState.OPEN, successState(vm).issue.state)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun reopenIssue_success_updatesStateToOpen_emitsReopenedSnackbar() =
        runTest {
            val mockRepo = repository(issue = issue(IssueState.CLOSED))
            coEvery { mockRepo.updateIssue(owner, repo, number, state = "open") } returns issue(IssueState.OPEN)
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.reopenIssue()
                advanceUntilIdle()
                assertEquals(IssueState.OPEN, successState(vm).issue.state)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ISSUE_REOPENED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- 评论增改删 ----

    @Test
    fun addComment_success_optimisticInsertThenReplaced_emitsCommentAdded() =
        runTest {
            val gate = CompletableDeferred<IssueComment>()
            val mockRepo = repository()
            coEvery { mockRepo.createComment(owner, repo, number, "Nice work") } coAnswers { gate.await() }
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.addComment("Nice work")
                runCurrent()
                // 乐观插入临时评论（仓库调用未完成）
                val optimistic = successState(vm)
                assertEquals(1, optimistic.timeline.size)
                val temp = optimistic.timeline[0] as IssueTimelineItem.Comment
                assertEquals("Nice work", temp.body)
                assertTrue("临时评论 id 应为负数合成", temp.id < 0)

                // 仓库返回 → 替换为真实评论
                gate.complete(IssueComment(id = 100L, body = "Nice work", author = IssueUser(login = "octocat")))
                advanceUntilIdle()
                val state = successState(vm)
                assertEquals(100L, (state.timeline[0] as IssueTimelineItem.Comment).id)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.COMMENT_ADDED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun addComment_failure_removesTempComment_emitsErrorSnackbar() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.createComment(any(), any(), any(), any()) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.addComment("Nice work")
                advanceUntilIdle()
                // 失败回滚：临时评论移除，时间线恢复空
                assertEquals(0, successState(vm).timeline.size)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun updateComment_success_updatesBody_emitsCommentUpdated() =
        runTest {
            val comment = IssueTimelineItem.Comment(id = 10L, author = IssueUser(login = "octocat"), body = "old")
            val mockRepo = repository(timeline = listOf(comment))
            coEvery { mockRepo.updateComment(owner, repo, 10L, "new") } returns IssueComment(id = 10L, body = "new")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.updateComment(10L, "new")
                advanceUntilIdle()
                val updated = successState(vm).timeline[0] as IssueTimelineItem.Comment
                assertEquals("new", updated.body)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.COMMENT_UPDATED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deleteComment_success_removesComment_emitsCommentDeleted() =
        runTest {
            val comment = IssueTimelineItem.Comment(id = 10L, author = IssueUser(login = "octocat"), body = "bye")
            val mockRepo = repository(timeline = listOf(comment))
            coEvery { mockRepo.deleteComment(owner, repo, 10L) } returns Unit
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.deleteComment(10L)
                advanceUntilIdle()
                assertEquals(0, successState(vm).timeline.size)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.COMMENT_DELETED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deleteComment_failure_restoresComment_emitsErrorSnackbar() =
        runTest {
            val comment = IssueTimelineItem.Comment(id = 10L, author = IssueUser(login = "octocat"), body = "bye")
            val mockRepo = repository(timeline = listOf(comment))
            coEvery { mockRepo.deleteComment(any(), any(), any()) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.deleteComment(10L)
                advanceUntilIdle()
                // 失败回滚：评论恢复
                assertEquals(1, successState(vm).timeline.size)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- 反应 toggle ----

    @Test
    fun toggleIssueReaction_add_success_incrementsCountAndTracksReaction() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.addIssueReaction(owner, repo, number, "heart") } returns IssueReaction(id = 7L, content = "heart")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleIssueReaction("heart")
                runCurrent()
                // 乐观：计数 +1
                assertEquals(1, successState(vm).issue.reactions.totalCount)
                advanceUntilIdle()
                val state = successState(vm)
                assertEquals("反应 id 应被跟踪供删除", 7L, state.myReactions[1L]?.get("heart"))
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.REACTION_ADDED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleIssueReaction_addThenRemove_fullCycle() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.addIssueReaction(owner, repo, number, "heart") } returns IssueReaction(id = 7L, content = "heart")
            coEvery { mockRepo.removeIssueReaction(owner, repo, number, 7L) } returns Unit
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                // 第一次点击 → 添加
                vm.toggleIssueReaction("heart")
                advanceUntilIdle()
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.REACTION_ADDED), awaitItem())
                assertEquals(7L, successState(vm).myReactions[1L]?.get("heart"))

                // 第二次点击 → 删除（乐观 -1 + 清除跟踪）
                vm.toggleIssueReaction("heart")
                runCurrent()
                assertEquals(0, successState(vm).issue.reactions.totalCount)
                advanceUntilIdle()
                assertTrue("删除后不应再跟踪该反应", successState(vm).myReactions[1L].orEmpty().isEmpty())
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.REACTION_REMOVED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleIssueReaction_add_failure_rollsBackCount() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.addIssueReaction(any(), any(), any(), any()) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleIssueReaction("heart")
                advanceUntilIdle()
                // 失败回滚：计数恢复 0
                assertEquals(0, successState(vm).issue.reactions.totalCount)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- 任务列表 checkbox 反向同步 ----

    @Test
    fun toggleTaskListItem_success_updatesBody_emitsTaskListUpdated() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.toggleTaskListItem(owner, repo, number, "I_kwDOA", "- [ ] task", 0, true) } returns
                issue().copy(body = "- [x] task")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleTaskListItem(0, true)
                runCurrent()
                // 乐观：body 立即翻转
                assertEquals("- [x] task", successState(vm).issue.body)
                advanceUntilIdle()
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.TASK_LIST_UPDATED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleTaskListItem_failure_rollsBackBody_emitsErrorSnackbar() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.toggleTaskListItem(any(), any(), any(), any(), any(), any(), any()) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleTaskListItem(0, true)
                advanceUntilIdle()
                // 失败回滚：body 恢复原样
                assertEquals("- [ ] task", successState(vm).issue.body)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleTaskListItem_indexOutOfRange_noOp() =
        runTest {
            val mockRepo = repository()
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleTaskListItem(9, true)
                advanceUntilIdle()
                assertEquals("- [ ] task", successState(vm).issue.body)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- 权限门控（viewerPermission） ----

    @Test
    fun permissionGating_authorWithWrite_canEditCloseCommentAndManageMeta() {
        val state =
            IssueDetailUiState.Success(
                issue = issue(),
                timeline = emptyList(),
                writeContext = writeContext(IssueViewerPermission.WRITE),
            )
        assertTrue(state.canEditIssue)
        assertTrue(state.canCloseReopen)
        assertTrue(state.canComment)
        assertTrue(state.canManageMeta)
    }

    @Test
    fun permissionGating_reader_cannotEditCloseOrManageMeta_butCanComment() {
        // 非作者 + READ 权限：不能编辑/关闭/管理元数据，但已登录可评论
        val state =
            IssueDetailUiState.Success(
                issue = issue().copy(author = IssueUser(login = "hubot")),
                timeline = emptyList(),
                writeContext = writeContext(IssueViewerPermission.READ),
            )
        assertFalse(state.canEditIssue)
        assertFalse(state.canCloseReopen)
        assertFalse(state.canManageMeta)
        assertTrue("已登录即可评论", state.canComment)
    }

    @Test
    fun permissionGating_anonymous_cannotCommentOrEdit() {
        val state =
            IssueDetailUiState.Success(
                issue = issue(),
                timeline = emptyList(),
                writeContext = IssueWriteContext(viewerLogin = null, viewerPermission = IssueViewerPermission.NONE),
            )
        assertFalse(state.canComment)
        assertFalse(state.canEditIssue)
        assertFalse(state.canCloseReopen)
    }

    @Test
    fun permissionGating_commentAuthor_canEditOwnCommentOnly() {
        val own = IssueTimelineItem.Comment(id = 10L, author = IssueUser(login = "octocat"))
        val other = IssueTimelineItem.Comment(id = 11L, author = IssueUser(login = "hubot"))
        val state =
            IssueDetailUiState.Success(
                issue = issue(),
                timeline = listOf(own, other),
                writeContext = writeContext(),
            )
        assertTrue(state.canEditComment(own))
        assertFalse(state.canEditComment(other))
    }

    @Test
    fun loadIssueDetail_mergesWriteContextIntoIssue() =
        runTest {
            val mockRepo = repository(context = writeContext(IssueViewerPermission.ADMIN))
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            val state = successState(vm)
            assertEquals("写上下文权限应合并进 Issue", IssueViewerPermission.ADMIN, state.issue.viewerPermission)
            assertEquals("写上下文 node id 应合并进 Issue", "I_kwDOA", state.issue.graphqlId)
        }
}
