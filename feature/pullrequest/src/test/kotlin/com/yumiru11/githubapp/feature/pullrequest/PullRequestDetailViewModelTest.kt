package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestBranch
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            val repo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws httpException
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Error)
            assertEquals(PullRequestErrorType.NOT_FOUND, (state as PullRequestDetailUiState.Error).errorType)
        }

    @Test
    fun load_ioError_emitsErrorNetwork() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws IOException("boom")
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Error)
            assertEquals(PullRequestErrorType.NETWORK, (state as PullRequestDetailUiState.Error).errorType)
        }

    @Test
    fun load_unknownError_emitsErrorUnknown() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(any(), any(), any()) } throws IllegalStateException("boom")
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestDetailUiState.Error)
            assertEquals(PullRequestErrorType.UNKNOWN, (state as PullRequestDetailUiState.Error).errorType)
        }

    @Test
    fun load_timelineFailure_emitsErrorNotPartialSuccess() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    coEvery { getPullRequest(owner, repo, number) } returns pullRequest()
                    coEvery { timeline(owner, repo, number) } throws IOException("timeline boom")
                }

            val viewModel = PullRequestDetailViewModel(savedStateHandle(), repo)

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
}
