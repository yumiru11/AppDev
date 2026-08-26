package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
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
 * BranchesViewModel 单测（T23，纯 JVM，MockK 桩 RepoRepository / RepoManagementRepository）。
 *
 * 覆盖：加载成功（默认分支排首 + canPush）、网络错误 → Error、无权限 → canPush=false；
 * 新建分支成功（事件 + 刷新）/ 空白名忽略 / 失败回弹；删除分支成功 / 失败事件。
 */
class BranchesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World"))

    private fun repository(
        control: BranchControl = BranchControl(canPush = true, defaultBranch = "main"),
        branches: List<Branch> = listOf(Branch(name = "dev"), Branch(name = "main"), Branch(name = "feat")),
    ): RepoRepository =
        mockk {
            coEvery { branchControl(any(), any()) } returns control
            coEvery { branches(any(), any()) } returns Result.success(branches)
            coEvery { createBranch(any(), any(), any(), any()) } returns Result.success(Unit)
        }

    private fun managementRepository(): RepoManagementRepository =
        mockk {
            coEvery { deleteBranch(any(), any(), any()) } returns Unit
        }

    private fun viewModel(
        repo: RepoRepository,
        mgmt: RepoManagementRepository = managementRepository(),
    ): BranchesViewModel = BranchesViewModel(savedStateHandle, repo, mgmt)

    @Test
    fun load_success_sortsDefaultFirstAndExposesCanPush() =
        runTest {
            val vm = viewModel(repository())

            val state = vm.uiState.value as BranchesUiState.Success

            assertEquals("main", state.branches.first().name)
            assertEquals("feat", state.branches.last().name)
            assertTrue(state.canPush)
        }

    @Test
    fun load_networkError_emitsErrorState() =
        runTest {
            val repo =
                mockk<RepoRepository> {
                    coEvery { branchControl(any(), any()) } returns BranchControl()
                    coEvery { branches(any(), any()) } returns Result.failure(IOException("boom"))
                    coEvery { createBranch(any(), any(), any(), any()) } returns Result.success(Unit)
                }

            val vm = viewModel(repo)

            assertEquals(RepoErrorType.NETWORK, (vm.uiState.value as BranchesUiState.Error).errorType)
        }

    @Test
    fun load_noPermission_canPushFalse() =
        runTest {
            val vm = viewModel(repository(control = BranchControl(canPush = false, defaultBranch = "main")))

            assertFalse((vm.uiState.value as BranchesUiState.Success).canPush)
        }

    @Test
    fun createBranch_success_emitsCreatedAndRefreshes() =
        runTest {
            val repo = repository()
            val vm = viewModel(repo)

            vm.events.test {
                vm.createBranch("feat-x")

                assertEquals(BranchEvent.Created("feat-x"), awaitItem())
            }

            coVerify(exactly = 1) { repo.createBranch("octocat", "Hello-World", "feat-x", "main") }
            assertFalse((vm.uiState.value as BranchesUiState.Success).isBusy)
        }

    @Test
    fun createBranch_blankName_ignored() =
        runTest {
            val repo = repository()
            val vm = viewModel(repo)

            vm.createBranch("   ")

            coVerify(exactly = 0) { repo.createBranch(any(), any(), any(), any()) }
        }

    @Test
    fun createBranch_failure_emitsFailedAndResetsBusy() =
        runTest {
            val repo =
                mockk<RepoRepository> {
                    coEvery { branchControl(any(), any()) } returns BranchControl(canPush = true, defaultBranch = "main")
                    coEvery { branches(any(), any()) } returns Result.success(listOf(Branch(name = "main")))
                    coEvery { createBranch(any(), any(), any(), any()) } returns Result.failure(IOException("boom"))
                }
            val vm = viewModel(repo)

            vm.events.test {
                vm.createBranch("feat-x")

                assertEquals(BranchEvent.Failed(RepoErrorType.NETWORK), awaitItem())
            }

            assertFalse((vm.uiState.value as BranchesUiState.Success).isBusy)
        }

    @Test
    fun deleteBranch_success_emitsDeletedAndRefreshes() =
        runTest {
            val mgmt = managementRepository()
            val vm = viewModel(repository(), mgmt)

            vm.events.test {
                vm.deleteBranch("feat")

                assertEquals(BranchEvent.Deleted("feat"), awaitItem())
            }

            coVerify(exactly = 1) { mgmt.deleteBranch("octocat", "Hello-World", "feat") }
        }

    @Test
    fun deleteBranch_failure_emitsFailed() =
        runTest {
            val mgmt =
                mockk<RepoManagementRepository> {
                    coEvery { deleteBranch(any(), any(), any()) } throws IOException("boom")
                }
            val vm = viewModel(repository(), mgmt)

            vm.events.test {
                vm.deleteBranch("feat")

                assertEquals(BranchEvent.Failed(RepoErrorType.NETWORK), awaitItem())
            }
        }
}
