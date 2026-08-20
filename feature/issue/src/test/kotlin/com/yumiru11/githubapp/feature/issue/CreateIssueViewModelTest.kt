package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * CreateIssueViewModel 单测（T14，MockK + Turbine）。
 *
 * 覆盖：创建成功 emit Created、标签逗号分隔解析、空标题不提交、失败 → Error 态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateIssueViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val owner = "octocat"
    private val repo = "Hello-World"

    private fun viewModel(repository: IssueRepository): CreateIssueViewModel =
        CreateIssueViewModel(
            SavedStateHandle(mapOf("owner" to owner, "repo" to repo)),
            repository,
        )

    @Test
    fun createIssue_success_emitsCreatedAndParsesLabels() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { createIssue(owner, repo, "New bug", "Details", listOf("bug", "ui")) } returns
                        Issue(id = 1L, number = 42, title = "New bug", state = IssueState.OPEN)
                }
            val vm = viewModel(repository)

            vm.events.test {
                vm.createIssue("New bug", "Details", "bug, ui")
                advanceUntilIdle()
                assertEquals(CreateIssueEvent.Created, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { repository.createIssue(owner, repo, "New bug", "Details", listOf("bug", "ui")) }
        }

    @Test
    fun createIssue_blankLabels_sendsNullLabels() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { createIssue(owner, repo, "t", "b", null) } returns
                        Issue(id = 1L, number = 1, title = "t", state = IssueState.OPEN)
                }
            val vm = viewModel(repository)

            vm.events.test {
                vm.createIssue("t", "b", "  ,  ")
                advanceUntilIdle()
                assertEquals(CreateIssueEvent.Created, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { repository.createIssue(owner, repo, "t", "b", null) }
        }

    @Test
    fun createIssue_blankTitle_doesNotSubmit() =
        runTest {
            val repository = mockk<IssueRepository>()
            val vm = viewModel(repository)

            vm.events.test {
                vm.createIssue("   ", "b", "")
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { repository.createIssue(any(), any(), any(), any(), any()) }
        }

    @Test
    fun createIssue_failure_emitsErrorState() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { createIssue(any(), any(), any(), any(), any()) } throws IOException("network down")
                }
            val vm = viewModel(repository)

            vm.createIssue("t", "b", "")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CreateIssueUiState.Error)
            assertEquals(IssueErrorType.NETWORK, (state as CreateIssueUiState.Error).errorType)
        }

    @Test
    fun createIssue_submitting_stateDuringFlight() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { createIssue(any(), any(), any(), any(), any()) } coAnswers {
                        kotlinx.coroutines.delay(100)
                        Issue(id = 1L, number = 1, title = "t", state = IssueState.OPEN)
                    }
                }
            val vm = viewModel(repository)

            vm.createIssue("t", "b", "")
            assertEquals(CreateIssueUiState.Submitting, vm.uiState.value)
            advanceUntilIdle()
            // 成功后保持 Submitting（UI 收到 Created 事件即返回列表页）
            assertEquals(CreateIssueUiState.Submitting, vm.uiState.value)
        }
}
