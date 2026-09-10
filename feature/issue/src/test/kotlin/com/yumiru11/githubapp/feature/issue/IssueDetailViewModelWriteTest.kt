@file:Suppress("LargeClass") // T14 写操作 + #163 订阅/元数据编辑测试聚合在同一 VM 测试类（文件内按票分段），拆分反损可读性

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueComment
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueMilestone
import com.yumiru11.githubapp.feature.issue.model.IssueReaction
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── #163 L01：Issue 订阅 ───────────────────────────────────────

    @Test
    fun loadIssueDetail_subscriptionProbeSucceeds_mapsSubscribedFlag() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.isSubscribed(owner, repo, number) } returns true
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            assertTrue(successState(vm).isSubscribed)
            assertTrue(successState(vm).canSubscribe)
        }

    @Test
    fun loadIssueDetail_subscriptionProbeFails_degradesToUnsubscribed() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.isSubscribed(owner, repo, number) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            assertTrue("订阅态探测失败不阻塞详情页", vm.uiState.value is IssueDetailUiState.Success)
            assertFalse(successState(vm).isSubscribed)
        }

    @Test
    fun toggleSubscription_subscribeSuccess_optimisticThenKeeps_emitsSubscribed() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val mockRepo = repository()
            coEvery { mockRepo.subscribe(owner, repo, number) } coAnswers { gate.await() }
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleSubscription()
                runCurrent()
                // 乐观更新：按钮立即切到已订阅 + pending 防连点（仓库调用未返回）
                assertTrue(successState(vm).isSubscribed)
                assertTrue(successState(vm).subscriptionPending)
                gate.complete(Unit)
                advanceUntilIdle()
                assertTrue(successState(vm).isSubscribed)
                assertFalse("完成后解除 pending", successState(vm).subscriptionPending)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.SUBSCRIBED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleSubscription_pending_ignoresSecondClick() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val mockRepo = repository()
            coEvery { mockRepo.subscribe(owner, repo, number) } coAnswers { gate.await() }
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.toggleSubscription()
            runCurrent()
            vm.toggleSubscription()
            runCurrent()

            gate.complete(Unit)
            advanceUntilIdle()
            coVerify(exactly = 1) { mockRepo.subscribe(owner, repo, number) }
        }

    @Test
    fun toggleSubscription_unsubscribeSuccess_emitsUnsubscribed() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.isSubscribed(owner, repo, number) } returns true
            coEvery { mockRepo.unsubscribe(owner, repo, number) } returns Unit
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleSubscription()
                advanceUntilIdle()
                assertFalse(successState(vm).isSubscribed)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.UNSUBSCRIBED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { mockRepo.unsubscribe(owner, repo, number) }
        }

    @Test
    fun toggleSubscription_failure_rollsBack_emitsErrorSnackbar() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.subscribe(owner, repo, number) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleSubscription()
                advanceUntilIdle()
                assertFalse("失败回滚为未订阅", successState(vm).isSubscribed)
                assertFalse(successState(vm).subscriptionPending)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleSubscription_anonymous_doesNothing() =
        runTest {
            val mockRepo =
                repository(context = IssueWriteContext(viewerLogin = null, viewerPermission = IssueViewerPermission.NONE))
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.events.test {
                vm.toggleSubscription()
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { mockRepo.subscribe(any(), any(), any()) }
        }

    // ── #163 L02：Labels / Assignees / Milestone 编辑 ────────────────

    private fun issueWithMeta(): Issue =
        issue().copy(
            labels = listOf(IssueLabel(name = "bug", color = "d73a4a")),
            assignees = listOf(IssueUser(login = "hubot")),
            milestone = IssueMilestone(title = "v1.0", number = 3L),
        )

    private fun candidateRepository(issue: Issue = issueWithMeta()): IssueRepository =
        repository(issue = issue).also { mockRepo ->
            coEvery { mockRepo.getLabels(owner, repo) } returns
                listOf(IssueLabel(name = "bug", color = "d73a4a"), IssueLabel(name = "ui", color = "1d76db"))
            coEvery { mockRepo.getAssignees(owner, repo) } returns
                listOf(IssueUser(login = "hubot", avatarUrl = "https://a/h.png"), IssueUser(login = "octocat"))
            coEvery { mockRepo.getMilestones(owner, repo) } returns
                listOf(IssueMilestone(title = "v1.0", number = 3L), IssueMilestone(title = "v2.0", number = 4L))
        }

    @Test
    fun openMetaEditor_withoutTriagePermission_doesNotLoadCandidates() =
        runTest {
            val mockRepo = repository(context = writeContext(IssueViewerPermission.READ))
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            assertFalse("READ 无元数据编辑入口", successState(vm).canManageMeta)
            vm.openMetaEditor()
            advanceUntilIdle()

            assertNull("无 TRIAGE+ 权限不打开编辑入口", vm.editState.value)
            coVerify(exactly = 0) { mockRepo.getLabels(any(), any()) }
            coVerify(exactly = 0) { mockRepo.getAssignees(any(), any()) }
            coVerify(exactly = 0) { mockRepo.getMilestones(any(), any()) }
        }

    @Test
    fun openMetaEditor_withPermission_loadsCandidatesAndPreselectsCurrent() =
        runTest {
            val mockRepo = candidateRepository()
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.openMetaEditor()
            advanceUntilIdle()

            val edit = requireNotNull(vm.editState.value)
            assertFalse(edit.loading)
            assertNull(edit.errorType)
            assertEquals(listOf("bug", "ui"), edit.labels.map { it.name })
            assertEquals(listOf("hubot", "octocat"), edit.assignees.map { it.login })
            assertEquals(listOf(3L, 4L), edit.milestones.map { it.number })
            assertEquals(setOf("bug"), edit.selectedLabels)
            assertEquals(setOf("hubot"), edit.selectedAssignees)
            assertEquals(3L, edit.selectedMilestone)
        }

    @Test
    fun openMetaEditor_labelsLoadFails_setsErrorStateWithRetry() =
        runTest {
            val mockRepo = repository(issue = issueWithMeta())
            coEvery { mockRepo.getLabels(owner, repo) } throws IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.openMetaEditor()
            advanceUntilIdle()

            val edit = requireNotNull(vm.editState.value)
            assertFalse(edit.loading)
            assertEquals(IssueErrorType.NETWORK, edit.errorType)
        }

    @Test
    fun openMetaEditor_assigneesFail_degradeToEmptyCandidates() =
        runTest {
            val mockRepo = repository(issue = issueWithMeta())
            coEvery { mockRepo.getLabels(owner, repo) } returns listOf(IssueLabel(name = "bug"))
            coEvery { mockRepo.getAssignees(owner, repo) } throws IOException("404 no permission")
            coEvery { mockRepo.getMilestones(owner, repo) } throws IOException("not found")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()

            vm.openMetaEditor()
            advanceUntilIdle()

            val edit = requireNotNull(vm.editState.value)
            assertNull("标签加载成功 → 不进入错误态", edit.errorType)
            assertTrue("Assignee 失败降级为空候选", edit.assignees.isEmpty())
            assertTrue("Milestone 失败降级为空候选", edit.milestones.isEmpty())
        }

    @Test
    fun selectionToggles_labelAssigneeMilestone_flipSelection() =
        runTest {
            val mockRepo = candidateRepository()
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.toggleLabelSelection("ui")
            vm.toggleAssigneeSelection("octocat")
            vm.selectMilestone(4L)

            val edit = requireNotNull(vm.editState.value)
            assertEquals(setOf("bug", "ui"), edit.selectedLabels)
            assertEquals(setOf("hubot", "octocat"), edit.selectedAssignees)
            assertEquals(4L, edit.selectedMilestone)

            // 再次点击 = 取消选择；Milestone 再点已选项 = 清除（null）
            vm.toggleLabelSelection("ui")
            vm.toggleAssigneeSelection("octocat")
            vm.selectMilestone(4L)
            val toggledBack = requireNotNull(vm.editState.value)
            assertEquals(setOf("bug"), toggledBack.selectedLabels)
            assertEquals(setOf("hubot"), toggledBack.selectedAssignees)
            assertNull(toggledBack.selectedMilestone)
        }

    @Test
    fun saveIssueMeta_onlyChangedFields_passedToRepository() =
        runTest {
            val mockRepo = candidateRepository()
            coEvery { mockRepo.updateIssueMeta(any(), any(), any(), any(), any(), any(), any()) } returns
                issueWithMeta().copy(labels = listOf(IssueLabel(name = "bug"), IssueLabel(name = "ui")))
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.toggleLabelSelection("ui")
            vm.saveIssueMeta()
            advanceUntilIdle()

            coVerify {
                mockRepo.updateIssueMeta(
                    owner = owner,
                    repo = repo,
                    number = number,
                    labels = listOf("bug", "ui"),
                    assignees = null,
                    milestone = null,
                    clearMilestone = false,
                )
            }
        }

    @Test
    fun saveIssueMeta_clearingMilestone_sendsClearFlag() =
        runTest {
            val mockRepo = candidateRepository()
            coEvery { mockRepo.updateIssueMeta(any(), any(), any(), any(), any(), any(), any()) } returns
                issueWithMeta().copy(milestone = null)
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.selectMilestone(null)
            vm.saveIssueMeta()
            advanceUntilIdle()

            coVerify {
                mockRepo.updateIssueMeta(
                    owner = owner,
                    repo = repo,
                    number = number,
                    labels = null,
                    assignees = null,
                    milestone = null,
                    clearMilestone = true,
                )
            }
        }

    @Test
    fun saveIssueMeta_success_optimisticHeaderThenServer_emitsMetaUpdated() =
        runTest {
            val mockRepo = candidateRepository()
            val updated = issueWithMeta().copy(assignees = listOf(IssueUser(login = "hubot"), IssueUser(login = "octocat")))
            val gate = CompletableDeferred<Issue>()
            coEvery { mockRepo.updateIssueMeta(any(), any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.events.test {
                vm.toggleAssigneeSelection("octocat")
                vm.saveIssueMeta()
                runCurrent()
                // 乐观更新：HeaderCard 立即出现新 assignee + Sheet 进入保存态
                assertEquals(listOf("hubot", "octocat"), successState(vm).issue.assignees.map { it.login })
                assertTrue(requireNotNull(vm.editState.value).saving)
                gate.complete(updated)
                advanceUntilIdle()
                assertEquals(listOf("hubot", "octocat"), successState(vm).issue.assignees.map { it.login })
                assertNull("保存成功关闭 Sheet", vm.editState.value)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ISSUE_META_UPDATED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveIssueMeta_failure_rollsBackHeaderAndKeepsSheetOpen() =
        runTest {
            val mockRepo = candidateRepository()
            coEvery { mockRepo.updateIssueMeta(any(), any(), any(), any(), any(), any(), any()) } throws
                IOException("network down")
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.events.test {
                vm.toggleLabelSelection("ui")
                vm.saveIssueMeta()
                advanceUntilIdle()
                assertEquals("失败回滚为服务端状态", listOf("bug"), successState(vm).issue.labels.map { it.name })
                val edit = requireNotNull(vm.editState.value)
                assertFalse("失败后解除保存态，保留用户选择", edit.saving)
                assertEquals(setOf("bug", "ui"), edit.selectedLabels)
                assertEquals(IssueDetailEvent.ShowSnackbar(IssueSnackbarMessage.ERROR_NETWORK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveIssueMeta_noChanges_closesSheetWithoutRequest() =
        runTest {
            val mockRepo = candidateRepository()
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.saveIssueMeta()
            advanceUntilIdle()

            assertNull(vm.editState.value)
            coVerify(exactly = 0) { mockRepo.updateIssueMeta(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun dismissMetaEditor_discardsSelection() =
        runTest {
            val mockRepo = candidateRepository()
            val vm = viewModel(mockRepo)
            advanceUntilIdle()
            vm.openMetaEditor()
            advanceUntilIdle()

            vm.toggleLabelSelection("ui")
            vm.dismissMetaEditor()

            assertNull(vm.editState.value)
        }
}
