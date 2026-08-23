@file:Suppress("LargeClass") // T15/T16/T17 三波测试聚合在同一 VM 测试类（文件内按票分段），拆分反损可读性（RepoFilesViewModelTest 同款先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.data.RepositoryControl
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentAnchor
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestBranch
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestMergeMethod
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReview
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReviewState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestUser
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestWriteAction
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThreadContext
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * PullRequestDetailViewModel 单测（纯 JVM，MockK 桩 PullRequestRepository）。
 *
 * 覆盖：成功加载 → Success（四 Tab 数据齐全）；404 → NOT_FOUND；IO → NETWORK；未知 → UNKNOWN；
 * retry 恢复；四 Tab 切换状态机；Checks 展开/收起；Commits 展开/收起；Files 展开/收起；
 * Mergeable 状态穿透到 Success。
 */
class PullRequestDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val owner = "octocat"
    private val repo = "Hello-World"
    private val number = 42

    private fun pullRequest(): PullRequest =
        PullRequest(
            id = 1L,
            number = number,
            title = "Add feature",
            state = PullRequestState.OPEN,
            mergeable = true,
            mergeableState = MergeableState.MERGEABLE,
            head = PullRequestBranch(ref = "feature", sha = "abc123"),
            base = PullRequestBranch(ref = "main"),
        )

    private fun timeline(): List<PullRequestTimelineItem> =
        listOf(
            PullRequestTimelineItem.Comment(
                id = 10L,
                author = null,
                body = "Looks good",
            ),
        )

    private fun checkRuns(): List<CheckRun> =
        listOf(
            CheckRun(
                id = 100L,
                name = "CI",
                status = CheckRunStatus.COMPLETED,
                conclusion = CheckRunConclusion.SUCCESS,
            ),
        )

    private fun savedStateHandle(): SavedStateHandle =
        SavedStateHandle(
            mapOf(
                "owner" to owner,
                "repo" to repo,
                "number" to number,
            ),
        )

    private fun repository(): PullRequestRepository =
        mockk<PullRequestRepository> {
            coEvery { getPullRequest(owner, repo, number) } returns pullRequest()
            coEvery { timeline(owner, repo, number) } returns timeline()
            coEvery { commits(owner, repo, number) } returns emptyList()
            coEvery { files(owner, repo, number) } returns emptyList()
            coEvery { checkRuns(owner, repo, "abc123") } returns checkRuns()
            coEvery { combinedStatus(owner, repo, "abc123") } returns CombinedStatus(state = "success", totalCount = 1)
            coEvery { reviewComments(owner, repo, number) } returns emptyList()
            coEvery { reviewThreadContext(any()) } returns ReviewThreadContext(pullRequestNodeId = "PR_1")
            coEvery { repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
        }

    @Test
    fun load_success_emitsSuccessWithAllTabData() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Success)
            state as PullRequestDetailUiState.Success
            assertEquals(pullRequest(), state.pullRequest)
            assertEquals(timeline(), state.timeline)
            assertEquals(checkRuns(), state.checkRuns)
            assertEquals("success", state.combinedStatus?.state)
            assertEquals(PullRequestTab.CONVERSATION, viewModel.selectedTab.value)
        }

    @Test
    fun load_mergeableState_passesThroughToSuccess() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(MergeableState.MERGEABLE, state.pullRequest.mergeableState)
            assertEquals(true, state.pullRequest.mergeable)
        }

    @Test
    fun load_404_emitsErrorNotFound() =
        runTest {
            val httpException =
                mockk<HttpException> {
                    every { code() } returns 404
                }
            val mockRepo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws httpException
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Error)
            assertEquals(PullRequestErrorType.NOT_FOUND, (state as PullRequestDetailUiState.Error).errorType)
        }

    @Test
    fun load_ioError_emitsErrorNetwork() =
        runTest {
            val mockRepo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws IOException("boom")
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Error)
            assertEquals(PullRequestErrorType.NETWORK, (state as PullRequestDetailUiState.Error).errorType)
        }

    @Test
    fun load_unknownError_emitsErrorUnknown() =
        runTest {
            val mockRepo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws IllegalStateException("boom")
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Error)
            assertEquals(PullRequestErrorType.UNKNOWN, (state as PullRequestDetailUiState.Error).errorType)
        }

    @Test
    fun load_timelineFailure_emitsErrorNotPartialSuccess() =
        runTest {
            val mockRepo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(owner, repo, number) } returns pullRequest()
                    coEvery { timeline(owner, repo, number) } throws IOException("timeline boom")
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value
            assertTrue("时间线失败 → 整体 Error（不产部分 Success）", state is PullRequestDetailUiState.Error)
        }

    @Test
    fun retry_afterError_recoversToSuccess() =
        runTest {
            val failingRepository =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws IOException("boom")
                }
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), failingRepository)
            assertTrue(viewModel.uiState.value is PullRequestDetailUiState.Error)

            // 恢复桩：重试后成功
            coEvery { failingRepository.getPullRequest(owner, repo, number) } returns pullRequest()
            coEvery { failingRepository.timeline(owner, repo, number) } returns timeline()
            coEvery { failingRepository.commits(owner, repo, number) } returns emptyList()
            coEvery { failingRepository.files(owner, repo, number) } returns emptyList()
            coEvery { failingRepository.checkRuns(owner, repo, "abc123") } returns checkRuns()
            coEvery { failingRepository.combinedStatus(owner, repo, "abc123") } returns CombinedStatus(state = "success", totalCount = 1)
            coEvery { failingRepository.reviewComments(owner, repo, number) } returns emptyList()
            coEvery { failingRepository.reviewThreadContext(any()) } returns ReviewThreadContext(pullRequestNodeId = "PR_1")
            coEvery { failingRepository.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")

            viewModel.retry()

            assertTrue(viewModel.uiState.value is PullRequestDetailUiState.Success)
        }

    @Test
    fun selectTab_switchesBetweenFourTabs() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.selectTab(PullRequestTab.COMMITS)
            assertEquals(PullRequestTab.COMMITS, viewModel.selectedTab.value)

            viewModel.selectTab(PullRequestTab.CHECKS)
            assertEquals(PullRequestTab.CHECKS, viewModel.selectedTab.value)

            viewModel.selectTab(PullRequestTab.FILES)
            assertEquals(PullRequestTab.FILES, viewModel.selectedTab.value)

            viewModel.selectTab(PullRequestTab.CONVERSATION)
            assertEquals(PullRequestTab.CONVERSATION, viewModel.selectedTab.value)
        }

    @Test
    fun selectTab_sameTab_isIdempotent() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.selectTab(PullRequestTab.CONVERSATION)

            assertEquals(PullRequestTab.CONVERSATION, viewModel.selectedTab.value)
        }

    @Test
    fun toggleCheckExpanded_addsAndRemovesId() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.toggleCheckExpanded(100L)
            assertTrue(100L in viewModel.expandedCheckIds.value)

            viewModel.toggleCheckExpanded(100L)
            assertFalse(100L in viewModel.expandedCheckIds.value)
        }

    @Test
    fun toggleCheckExpanded_multipleIds_keepsIndependent() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.toggleCheckExpanded(100L)
            viewModel.toggleCheckExpanded(101L)

            assertTrue(100L in viewModel.expandedCheckIds.value)
            assertTrue(101L in viewModel.expandedCheckIds.value)

            viewModel.toggleCheckExpanded(100L)

            assertFalse(100L in viewModel.expandedCheckIds.value)
            assertTrue(101L in viewModel.expandedCheckIds.value)
        }

    @Test
    fun toggleCommitExpanded_addsAndRemovesSha() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.toggleCommitExpanded("abc123")
            assertTrue("abc123" in viewModel.expandedCommitShas.value)

            viewModel.toggleCommitExpanded("abc123")
            assertFalse("abc123" in viewModel.expandedCommitShas.value)
        }

    @Test
    fun toggleFileExpanded_addsAndRemovesFilename() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.toggleFileExpanded("src/Main.kt")
            assertTrue("src/Main.kt" in viewModel.expandedFileNames.value)

            viewModel.toggleFileExpanded("src/Main.kt")
            assertFalse("src/Main.kt" in viewModel.expandedFileNames.value)
        }

    // ── T16：行评论与会话解析 ──

    private fun reviewComment(
        id: Long,
        path: String = "README.md",
        side: DiffSide = DiffSide.RIGHT,
        line: Int? = 3,
        body: String = "nice",
    ): ReviewComment =
        ReviewComment(
            id = id,
            body = body,
            path = path,
            side = side,
            line = if (side == DiffSide.RIGHT) line else null,
            originalLine = if (side == DiffSide.LEFT) line else null,
        )

    private fun reviewThread(
        id: String = "THREAD_1",
        path: String = "README.md",
        side: DiffSide = DiffSide.RIGHT,
        isResolved: Boolean = false,
    ): ReviewThread = ReviewThread(id = id, path = path, side = side, line = 3, isResolved = isResolved)

    private fun repositoryWithComments(
        comments: List<ReviewComment> = emptyList(),
        threads: List<ReviewThread> = emptyList(),
        nodeId: String? = "PR_1",
    ): PullRequestRepository =
        mockk<PullRequestRepository> {
            coEvery { getPullRequest(owner, repo, number) } returns pullRequest()
            coEvery { timeline(owner, repo, number) } returns timeline()
            coEvery { commits(owner, repo, number) } returns emptyList()
            coEvery { files(owner, repo, number) } returns emptyList()
            coEvery { checkRuns(owner, repo, "abc123") } returns checkRuns()
            coEvery { combinedStatus(owner, repo, "abc123") } returns CombinedStatus(state = "success", totalCount = 1)
            coEvery { reviewComments(owner, repo, number) } returns comments
            coEvery { reviewThreadContext(any()) } returns ReviewThreadContext(pullRequestNodeId = nodeId, threads = threads)
            coEvery { repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
        }

    @Test
    fun load_success_carriesReviewCommentsAndThreads() =
        runTest {
            val comments = listOf(reviewComment(id = 1L))
            val threads = listOf(reviewThread())
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repositoryWithComments(comments = comments, threads = threads))

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(comments, state.reviewComments)
            assertEquals(threads, state.reviewThreads)
            assertTrue("GraphQL 会话可用 → 可解析", state.canResolveThreads)
        }

    @Test
    fun openLineComment_aggregatesThreadAndComments() =
        runTest {
            val viewModel =
                PullRequestDetailViewModel(
                    savedStateHandle(),
                    repositoryWithComments(comments = listOf(reviewComment(id = 1L)), threads = listOf(reviewThread())),
                )

            viewModel.openLineComment("README.md", DiffSide.RIGHT, 3)

            val target = viewModel.lineCommentTarget.value
            assertNotNull(target)
            assertEquals("README.md", target?.anchor?.path)
            assertEquals(3, target?.anchor?.line)
            assertEquals("THREAD_1", target?.thread?.id)
            assertEquals(1, target?.comments?.size)
        }

    @Test
    fun dismissLineComment_clearsTarget() =
        runTest {
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repository())

            viewModel.openLineComment("README.md", DiffSide.RIGHT, 3)
            assertNotNull(viewModel.lineCommentTarget.value)

            viewModel.dismissLineComment()

            assertNull(viewModel.lineCommentTarget.value)
        }

    @Test
    fun submitLineComment_success_replacesOptimisticAndCloses() =
        runTest {
            val created = reviewComment(id = 500L, body = "hi")
            val mockRepo = repositoryWithComments()
            coEvery { mockRepo.createReviewComment(owner, repo, number, any(), "hi", "abc123") } returns created

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val anchor = LineCommentAnchor(path = "README.md", side = DiffSide.RIGHT, line = 3)
            viewModel.submitLineComment(anchor, "hi")
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(1, state.reviewComments.size)
            assertEquals(500L, state.reviewComments.single().id)
            assertNull(viewModel.lineCommentTarget.value)
            coVerify(exactly = 1) { mockRepo.createReviewComment(owner, repo, number, anchor, "hi", "abc123") }
        }

    @Test
    fun submitLineComment_reply_callsReplyEndpoint() =
        runTest {
            val reply = reviewComment(id = 501L, body = "reply")
            val mockRepo = repositoryWithComments(comments = listOf(reviewComment(id = 1L)))
            coEvery { mockRepo.replyReviewComment(owner, repo, number, "README.md", 1L, "reply") } returns reply

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val anchor = LineCommentAnchor(path = "README.md", side = DiffSide.RIGHT, line = 3)
            viewModel.submitLineComment(anchor, "reply", inReplyToId = 1L)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(2, state.reviewComments.size)
            coVerify(exactly = 1) { mockRepo.replyReviewComment(owner, repo, number, "README.md", 1L, "reply") }
        }

    @Test
    fun submitLineComment_failure_rollsBackAndKeepsSheetOpen() =
        runTest {
            val mockRepo = repositoryWithComments()
            coEvery { mockRepo.createReviewComment(owner, repo, number, any(), any(), any()) } throws IOException("boom")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.openLineComment("README.md", DiffSide.RIGHT, 3)
            viewModel.submitLineComment(LineCommentAnchor(path = "README.md", side = DiffSide.RIGHT, line = 3), "hi")
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue("失败回滚 → 无残留乐观评论", state.reviewComments.isEmpty())
            assertNotNull("失败不关闭 sheet，保留输入", viewModel.lineCommentTarget.value)
        }

    @Test
    fun toggleThreadResolved_success_flipsResolved() =
        runTest {
            val mockRepo = repositoryWithComments(threads = listOf(reviewThread()))
            coEvery { mockRepo.setThreadResolved("THREAD_1", true) } returns Unit
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val thread = (viewModel.uiState.value as PullRequestDetailUiState.Success).reviewThreads.single()
            assertFalse(thread.isResolved)

            viewModel.toggleThreadResolved(thread)
            advanceUntilIdle()

            assertTrue((viewModel.uiState.value as PullRequestDetailUiState.Success).reviewThreads.single().isResolved)
            coVerify(exactly = 1) { mockRepo.setThreadResolved("THREAD_1", true) }
        }

    @Test
    fun toggleThreadResolved_failure_rollsBack() =
        runTest {
            val mockRepo = repositoryWithComments(threads = listOf(reviewThread()))
            coEvery { mockRepo.setThreadResolved("THREAD_1", true) } throws IOException("boom")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val thread = (viewModel.uiState.value as PullRequestDetailUiState.Success).reviewThreads.single()
            viewModel.toggleThreadResolved(thread)
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as PullRequestDetailUiState.Success).reviewThreads.single().isResolved)
        }

    @Test
    fun toggleThreadResolved_restOnlySession_isNoop() =
        runTest {
            val mockRepo = repositoryWithComments(threads = listOf(reviewThread()), nodeId = null)
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertFalse("REST-only 会话 → 无解析入口", state.canResolveThreads)

            viewModel.toggleThreadResolved(state.reviewThreads.single())
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as PullRequestDetailUiState.Success).reviewThreads.single().isResolved)
            coVerify(exactly = 0) { mockRepo.setThreadResolved(any(), any()) }
        }

    // ── T17：Review / Merge / Update branch / 删除分支 ──

    /** 同仓库 head 的 PR（Update branch / 删除分支可见性前提） */
    private fun sameRepoPullRequest(): PullRequest =
        pullRequest().copy(head = PullRequestBranch(ref = "feature", sha = "abc123", repoFullName = "octocat/Hello-World"))

    @Test
    fun load_success_writePermission_mapsActionFlags() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue(state.canReview)
            assertTrue(state.canApprove)
            assertTrue(state.canMerge)
            assertFalse("非同仓库 head → 不可删分支", state.canDeleteHeadBranch)
        }

    @Test
    fun load_success_readPermission_hidesApproveAndMerge() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.READ, defaultBranch = "main")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue("READ 可发起 comment 审查", state.canReview)
            assertFalse(state.canApprove)
            assertFalse(state.canMerge)
        }

    @Test
    fun load_success_unknownPermission_hidesAllWrites() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns RepositoryControl()

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertFalse(state.canReview)
            assertFalse(state.canApprove)
            assertFalse(state.canMerge)
        }

    @Test
    fun load_success_sameRepoHead_mapsDeleteBranchFlag() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue(state.headSameRepo)
            assertFalse("feature ≠ main 默认分支 → 可删", state.headIsDefaultBranch)
            assertTrue(state.canDeleteHeadBranch)
        }

    @Test
    fun submitReview_success_optimisticInsertThenReplaced() =
        runTest {
            val mockRepo = repository()
            val review =
                PullRequestReview(
                    id = 900L,
                    author = PullRequestUser(login = "reviewer"),
                    body = "LGTM",
                    state = PullRequestReviewState.APPROVED,
                )
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.submitReview(owner, repo, number, ReviewConclusion.APPROVE, "LGTM") } coAnswers {
                gate.await()
                review
            }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.submitReview(ReviewConclusion.APPROVE, "LGTM")

            val optimistic = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(PullRequestWriteAction.REVIEW, optimistic.pendingAction)
            val optimisticItem = optimistic.timeline.first() as PullRequestTimelineItem.Review
            assertTrue("乐观临时 id 为负", optimisticItem.id < 0)
            assertEquals(PullRequestReviewState.APPROVED, optimisticItem.state)

            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertNull(state.pendingAction)
            val item = state.timeline.first() as PullRequestTimelineItem.Review
            assertEquals(900L, item.id)
            assertEquals("reviewer", item.author?.login)
            coVerify(exactly = 1) { mockRepo.submitReview(owner, repo, number, ReviewConclusion.APPROVE, "LGTM") }
        }

    @Test
    fun submitReview_failure_rollsBackOptimisticAndClearsPending() =
        runTest {
            val mockRepo = repository()
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.submitReview(owner, repo, number, ReviewConclusion.COMMENT, "hi") } coAnswers {
                gate.await()
                throw IOException("boom")
            }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.submitReview(ReviewConclusion.COMMENT, "hi")

            val optimistic = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(1, optimistic.timeline.filterIsInstance<PullRequestTimelineItem.Review>().size)

            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertNull(state.pendingAction)
            assertTrue("失败回滚 → 无残留 Review", state.timeline.none { it is PullRequestTimelineItem.Review })
        }

    @Test
    fun submitReview_approveWithoutWritePermission_isIgnored() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.READ, defaultBranch = "main")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertFalse(state.canApprove)

            viewModel.submitReview(ReviewConclusion.APPROVE, "LGTM")
            advanceUntilIdle()

            coVerify(exactly = 0) { mockRepo.submitReview(any(), any(), any(), any(), any()) }
        }

    @Test
    fun merge_success_optimisticMergedThenConfirmed() =
        runTest {
            val mockRepo = repository()
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.mergePullRequest(owner, repo, number, PullRequestMergeMethod.SQUASH, "title", "note", "abc123") } coAnswers {
                gate.await()
                true
            }
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            // 加载态为 OPEN；合并成功后的刷新返回 MERGED（合并前建桩会令 canMerge=false）
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns
                pullRequest().copy(state = PullRequestState.MERGED, mergedAt = "2026-08-23T00:00:00Z")
            viewModel.mergePullRequest(PullRequestMergeMethod.SQUASH, "title", "note", deleteBranch = false)

            val optimistic = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(PullRequestState.MERGED, optimistic.pullRequest.state)
            assertEquals(PullRequestWriteAction.MERGE, optimistic.pendingAction)

            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertNull(state.pendingAction)
            assertEquals("刷新保持 MERGED", PullRequestState.MERGED, state.pullRequest.state)
            coVerify(
                exactly = 1,
            ) { mockRepo.mergePullRequest(owner, repo, number, PullRequestMergeMethod.SQUASH, "title", "note", "abc123") }
        }

    @Test
    fun merge_failure_rollsBackOpenAndRestoresCanMerge() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.mergePullRequest(owner, repo, number, any(), any(), any(), any()) } throws IOException("boom")

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.mergePullRequest(PullRequestMergeMethod.MERGE, "", "", deleteBranch = false)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(PullRequestState.OPEN, state.pullRequest.state)
            assertNull(state.pendingAction)
            assertTrue("回滚恢复可合并", state.canMerge)
        }

    @Test
    fun mergeWithDeleteBranch_success_deletesHeadBranchAndHidesEntry() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            coEvery { mockRepo.mergePullRequest(owner, repo, number, any(), any(), any(), "abc123") } returns true
            coEvery { mockRepo.deleteBranch(owner, repo, "feature") } returns Unit

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val loaded = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue(loaded.canDeleteHeadBranch)

            // 合并成功后刷新返回 MERGED 态
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest().copy(state = PullRequestState.MERGED)
            viewModel.mergePullRequest(PullRequestMergeMethod.MERGE, "", "", deleteBranch = true)
            advanceUntilIdle()

            coVerify(exactly = 1) { mockRepo.deleteBranch(owner, repo, "feature") }
            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertFalse("删除后隐藏入口", state.canDeleteHeadBranch)
        }

    @Test
    fun merge_whileMergePending_isIgnored() =
        runTest {
            val mockRepo = repository()
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.mergePullRequest(owner, repo, number, any(), any(), any(), any()) } coAnswers {
                gate.await()
                true
            }
            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            // 加载态为 OPEN；合并成功后的刷新返回 MERGED（合并前建桩会令 canMerge=false）
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns pullRequest().copy(state = PullRequestState.MERGED)
            viewModel.mergePullRequest(PullRequestMergeMethod.MERGE, "", "", deleteBranch = false)
            viewModel.mergePullRequest(PullRequestMergeMethod.SQUASH, "", "", deleteBranch = false)
            gate.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { mockRepo.mergePullRequest(owner, repo, number, any(), any(), any(), any()) }
        }

    @Test
    fun updateBranch_success_clearsPending() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.updateBranch(owner, repo, number, "abc123") } coAnswers {
                gate.await()
                Unit
            }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.updateBranch()

            val optimistic = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertEquals(PullRequestWriteAction.UPDATE_BRANCH, optimistic.pendingAction)

            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertNull(state.pendingAction)
            coVerify(exactly = 1) { mockRepo.updateBranch(owner, repo, number, "abc123") }
        }

    @Test
    fun updateBranch_failure_clearsPending() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.updateBranch(owner, repo, number, "abc123") } coAnswers {
                gate.await()
                throw IOException("boom")
            }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.updateBranch()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertNull(state.pendingAction)
            coVerify(exactly = 1) { mockRepo.updateBranch(owner, repo, number, "abc123") }
        }

    @Test
    fun deleteBranch_success_clearsFlag() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.deleteBranch(owner, repo, "feature") } coAnswers {
                gate.await()
                Unit
            }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            val loaded = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue(loaded.canDeleteHeadBranch)

            viewModel.deleteBranch()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertFalse(state.canDeleteHeadBranch)
            coVerify(exactly = 1) { mockRepo.deleteBranch(owner, repo, "feature") }
        }

    @Test
    fun deleteBranch_failure_keepsEntry() =
        runTest {
            val mockRepo = repository()
            coEvery { mockRepo.getPullRequest(owner, repo, number) } returns sameRepoPullRequest()
            coEvery { mockRepo.repositoryControl(owner, repo) } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            val gate = CompletableDeferred<Unit>()
            coEvery { mockRepo.deleteBranch(owner, repo, "feature") } coAnswers {
                gate.await()
                throw IOException("boom")
            }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), mockRepo)
            viewModel.deleteBranch()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PullRequestDetailUiState.Success
            assertTrue("失败保留入口可重试", state.canDeleteHeadBranch)
        }
}
