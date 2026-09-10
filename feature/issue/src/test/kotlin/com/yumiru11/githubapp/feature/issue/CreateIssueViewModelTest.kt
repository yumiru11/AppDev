package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
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
 * CreateIssueViewModel 单测（T14 + #163 L02）。
 *
 * 覆盖：创建成功 emit Created、选中标签作为列表提交、未选标签 → null、空标题不提交、
 * 失败 → Error 态；#163：标签候选项加载（成功/失败降级空列表，创建表单不受影响）。
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
    fun createIssue_success_emitsCreatedAndSendsSelectedLabels() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getLabels(owner, repo) } returns emptyList()
                    coEvery { createIssue(owner, repo, "New bug", "Details", listOf("bug", "ui")) } returns
                        Issue(id = 1L, number = 42, title = "New bug", state = IssueState.OPEN)
                }
            val vm = viewModel(repository)

            vm.events.test {
                vm.createIssue("New bug", "Details", listOf("bug", "ui"))
                advanceUntilIdle()
                assertEquals(CreateIssueEvent.Created, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { repository.createIssue(owner, repo, "New bug", "Details", listOf("bug", "ui")) }
        }

    @Test
    fun createIssue_noLabelsSelected_sendsNullLabels() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getLabels(owner, repo) } returns emptyList()
                    coEvery { createIssue(owner, repo, "t", "b", null) } returns
                        Issue(id = 1L, number = 1, title = "t", state = IssueState.OPEN)
                }
            val vm = viewModel(repository)

            vm.events.test {
                vm.createIssue("t", "b", emptyList())
                advanceUntilIdle()
                assertEquals(CreateIssueEvent.Created, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { repository.createIssue(owner, repo, "t", "b", null) }
        }

    @Test
    fun createIssue_blankTitle_doesNotSubmit() =
        runTest {
            val repository = mockk<IssueRepository> { coEvery { getLabels(owner, repo) } returns emptyList() }
            val vm = viewModel(repository)

            vm.events.test {
                vm.createIssue("   ", "b", emptyList())
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
                    coEvery { getLabels(owner, repo) } returns emptyList()
                    coEvery { createIssue(any(), any(), any(), any(), any()) } throws IOException("network down")
                }
            val vm = viewModel(repository)

            vm.createIssue("t", "b", emptyList())
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
                    coEvery { getLabels(owner, repo) } returns emptyList()
                    coEvery { createIssue(any(), any(), any(), any(), any()) } coAnswers {
                        kotlinx.coroutines.delay(100)
                        Issue(id = 1L, number = 1, title = "t", state = IssueState.OPEN)
                    }
                }
            val vm = viewModel(repository)

            vm.createIssue("t", "b", emptyList())
            assertEquals(CreateIssueUiState.Submitting, vm.uiState.value)
            advanceUntilIdle()
            // 成功后保持 Submitting（UI 收到 Created 事件即返回列表页）
            assertEquals(CreateIssueUiState.Submitting, vm.uiState.value)
        }

    // ── #163 L02：标签候选项 ────────────────────────────────────────

    @Test
    fun init_labelsLoadSucceeds_exposesCandidates() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getLabels(owner, repo) } returns
                        listOf(IssueLabel(name = "bug", color = "d73a4a"), IssueLabel(name = "ui"))
                }
            val vm = viewModel(repository)

            advanceUntilIdle()

            assertEquals(listOf("bug", "ui"), vm.availableLabels.value.map { it.name })
        }

    @Test
    fun init_labelsLoadFails_degradesToEmptyCandidates() =
        runTest {
            val repository =
                mockk<IssueRepository> {
                    coEvery { getLabels(owner, repo) } throws IOException("network down")
                }
            val vm = viewModel(repository)

            advanceUntilIdle()

            assertTrue("候选项加载失败 → 空列表（仍可无标签提交）", vm.availableLabels.value.isEmpty())
            assertEquals(CreateIssueUiState.Idle, vm.uiState.value)
        }
}
