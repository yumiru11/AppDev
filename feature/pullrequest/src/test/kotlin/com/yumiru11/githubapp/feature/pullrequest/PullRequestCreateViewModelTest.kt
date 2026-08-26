package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.data.RepositoryControl
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * PullRequestCreateViewModel 单测（T23，纯 JVM，MockK 桩 PullRequestRepository）。
 *
 * 覆盖：加载成功（base=默认分支、head=首个非 base 分支、canCreate）、单分支 head 为空、
 * 只读权限 canCreate=false、网络错误 → Error；提交成功（请求参数 + Created 事件）、
 * 标题空白/同分支忽略、失败回弹 + Failed 事件。
 */
class PullRequestCreateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World"))

    private fun repository(
        control: RepositoryControl = RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main"),
        branches: List<String> = listOf("main", "dev"),
    ): PullRequestRepository =
        mockk {
            coEvery { repositoryControl(any(), any()) } returns control
            coEvery { branches(any(), any()) } returns Result.success(branches)
            coEvery { createPullRequest(any(), any(), any(), any(), any(), any()) } returns
                PullRequest(id = 1, number = 42, title = "t", state = PullRequestState.OPEN)
        }

    private fun viewModel(repo: PullRequestRepository): PullRequestCreateViewModel = PullRequestCreateViewModel(savedStateHandle, repo)

    @Test
    fun load_success_setsDefaultBaseAndOtherHead() =
        runTest {
            val vm = viewModel(repository())

            val state = vm.uiState.value as PullRequestCreateUiState.Form

            assertEquals("main", state.baseBranch)
            assertEquals("dev", state.headBranch)
            assertEquals(2, state.branches.size)
            assertTrue(state.canCreate)
        }

    @Test
    fun load_singleBranch_headBlank() =
        runTest {
            val vm = viewModel(repository(branches = listOf("main")))

            val state = vm.uiState.value as PullRequestCreateUiState.Form

            assertEquals("main", state.baseBranch)
            assertEquals("", state.headBranch)
        }

    @Test
    fun load_readPermission_canCreateFalse() =
        runTest {
            val vm =
                viewModel(
                    repository(control = RepositoryControl(viewerPermission = ViewerPermission.READ, defaultBranch = "main")),
                )

            assertFalse((vm.uiState.value as PullRequestCreateUiState.Form).canCreate)
        }

    @Test
    fun load_networkError_emitsError() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    coEvery { repositoryControl(any(), any()) } returns RepositoryControl()
                    coEvery { branches(any(), any()) } returns Result.failure(IOException("boom"))
                }

            val vm = viewModel(repo)

            assertTrue(vm.uiState.value is PullRequestCreateUiState.Error)
        }

    @Test
    fun submit_valid_callsRepositoryAndEmitsCreated() =
        runTest {
            val repo = repository()
            val vm = viewModel(repo)
            vm.updateTitle("Add feature")
            vm.updateBody("Desc")

            vm.events.test {
                vm.submit()

                assertEquals(PullRequestCreateEvent.Created(42), awaitItem())
            }

            coVerify(exactly = 1) {
                repo.createPullRequest("octocat", "Hello-World", "Add feature", "Desc", "dev", "main")
            }
            assertFalse((vm.uiState.value as PullRequestCreateUiState.Form).isSubmitting)
        }

    @Test
    fun submit_blankTitle_noCall() =
        runTest {
            val repo = repository()
            val vm = viewModel(repo)

            vm.submit()

            coVerify(exactly = 0) { repo.createPullRequest(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun submit_sameBranches_noCall() =
        runTest {
            val repo = repository()
            val vm = viewModel(repo)
            vm.updateTitle("T")
            vm.selectHead("main")

            vm.submit()

            coVerify(exactly = 0) { repo.createPullRequest(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun submit_failure_resetsSubmittingAndEmitsFailed() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    coEvery { repositoryControl(any(), any()) } returns
                        RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
                    coEvery { branches(any(), any()) } returns Result.success(listOf("main", "dev"))
                    coEvery { createPullRequest(any(), any(), any(), any(), any(), any()) } throws IOException("boom")
                }
            val vm = viewModel(repo)
            vm.updateTitle("T")

            vm.events.test {
                vm.submit()

                assertEquals(PullRequestCreateEvent.Failed(PullRequestErrorType.NETWORK), awaitItem())
            }

            assertFalse((vm.uiState.value as PullRequestCreateUiState.Form).isSubmitting)
        }
}
