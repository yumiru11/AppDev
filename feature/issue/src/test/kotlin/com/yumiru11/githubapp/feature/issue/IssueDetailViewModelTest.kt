package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * IssueDetailViewModel 单测（纯 JVM，MockK 桩 IssueRepository）。
 *
 * 覆盖：成功加载 → Success；404 → NOT_FOUND；IO → NETWORK；未知 → UNKNOWN；retry 恢复；
 * 时间线失败 → Error（不产部分 Success）；加载期间保持 Loading（时间线不提前渲染）；
 * PR 判别（isPullRequest）穿透到 Success 状态。
 */
class IssueDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val owner = "octocat"
    private val repo = "Hello-World"
    private val number = 1

    private fun issue(): Issue =
        Issue(
            id = 1L,
            number = number,
            title = "Bug report",
            state = IssueState.OPEN,
        )

    private fun timeline(): List<IssueTimelineItem> =
        listOf(
            IssueTimelineItem.Comment(
                id = 10L,
                author = null,
                body = "Looks good",
            ),
            IssueTimelineItem.Event(
                id = 11L,
                type = IssueTimelineEventType.CLOSED,
                actor = null,
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

    @Test
    fun load_success_emitsSuccess() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(owner, repo, number) } returns issue()
                    coEvery { timeline(owner, repo, number) } returns timeline()
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            val state = viewModel.uiState.value
            assertTrue(state is IssueDetailUiState.Success)
            state as IssueDetailUiState.Success
            assertEquals(issue(), state.issue)
            assertEquals(timeline(), state.timeline)
        }

    @Test
    fun load_404_emitsErrorNotFound() =
        runTest {
            val httpException =
                mockk<HttpException> {
                    every { code() } returns 404
                }
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(any(), any(), any()) } throws httpException
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            assertEquals(
                IssueDetailUiState.Error(IssueErrorType.NOT_FOUND),
                viewModel.uiState.value,
            )
        }

    @Test
    fun load_networkError_emitsErrorNetwork() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(any(), any(), any()) } throws IOException("network down")
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            assertEquals(
                IssueDetailUiState.Error(IssueErrorType.NETWORK),
                viewModel.uiState.value,
            )
        }

    @Test
    fun load_unknownError_emitsErrorUnknown() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(any(), any(), any()) } throws IllegalStateException("boom")
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            assertEquals(
                IssueDetailUiState.Error(IssueErrorType.UNKNOWN),
                viewModel.uiState.value,
            )
        }

    @Test
    fun retry_afterError_reloadsAndSucceeds() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(owner, repo, number) } throws IOException("network down")
                }
            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)
            assertEquals(
                IssueDetailUiState.Error(IssueErrorType.NETWORK),
                viewModel.uiState.value,
            )

            coEvery { repository.getIssue(owner, repo, number) } returns issue()
            coEvery { repository.timeline(owner, repo, number) } returns timeline()
            viewModel.retry()

            assertTrue(viewModel.uiState.value is IssueDetailUiState.Success)
        }

    @Test
    fun load_timelineThrows_emitsErrorNetwork() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(owner, repo, number) } returns issue()
                    coEvery { timeline(any(), any(), any()) } throws IOException("network down")
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            // 详情成功但时间线失败 → 整体 Error（不产“详情成功/时间线缺失”的部分 Success）
            assertEquals(
                IssueDetailUiState.Error(IssueErrorType.NETWORK),
                viewModel.uiState.value,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun load_issueAndTimelineLoading_staysLoadingUntilBothComplete() =
        runTest {
            val gate = CompletableDeferred<Issue>()
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(owner, repo, number) } coAnswers { gate.await() }
                    coEvery { timeline(owner, repo, number) } returns timeline()
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            // 详情请求未完成 → 时间线不提前渲染，保持 Loading
            assertEquals(IssueDetailUiState.Loading, viewModel.uiState.value)

            gate.complete(issue())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is IssueDetailUiState.Success)
            state as IssueDetailUiState.Success
            assertEquals(issue(), state.issue)
            assertEquals(timeline(), state.timeline)
        }

    @Test
    fun load_prIssue_preservesPullRequestFlag() =
        runTest {
            val prIssue = issue().copy(isPullRequest = true)
            val repository =
                mockk<IssueRepository> {
                    coEvery { getIssue(owner, repo, number) } returns prIssue
                    coEvery { timeline(owner, repo, number) } returns timeline()
                }

            val viewModel = IssueDetailViewModel(savedStateHandle(), repository)

            val state = viewModel.uiState.value
            assertTrue(state is IssueDetailUiState.Success)
            assertTrue((state as IssueDetailUiState.Success).issue.isPullRequest)
        }
}
