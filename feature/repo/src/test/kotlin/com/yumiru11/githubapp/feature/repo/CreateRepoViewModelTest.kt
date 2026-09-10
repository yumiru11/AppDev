package com.yumiru11.githubapp.feature.repo

import app.cash.turbine.test
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * [CreateRepoViewModel] 单测（L04）。
 *
 * 覆盖：名称实时校验（空白不飘红）、提交前校验拦截、创建成功 → created 落点、
 * 422 → NameTaken、403 → PermissionDenied、网络 → Failed、提交中防重入、
 * 描述空白裁剪为 null。
 */
class CreateRepoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val created = GitHubFakes.fakeRepository(ownerLogin = "octocat", name = "new-repo")

    private fun viewModel(result: Result<Repository> = Result.success(created)): CreateRepoViewModel {
        val repository =
            mockk<RepoAdminRepository> {
                coEvery { createRepository(any(), any(), any(), any(), any(), any()) } returns result
            }
        return CreateRepoViewModel(repository)
    }

    @Test
    fun onNameChange_validName_clearsErrorAndEnablesSubmit() {
        val vm = viewModel()

        vm.onNameChange("hello-world")

        assertEquals("hello-world", vm.uiState.value.name)
        assertNull(vm.uiState.value.nameError)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun onNameChange_invalidName_setsErrorAndDisablesSubmit() {
        val vm = viewModel()

        vm.onNameChange("hello world")

        assertEquals(RepoNameError.INVALID_CHARS, vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun onNameChange_blank_doesNotShowError() {
        val vm = viewModel()

        vm.onNameChange("")

        assertNull("刚进页面不应飘红", vm.uiState.value.nameError)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun submit_invalidName_doesNotCallRepository() =
        runTest {
            val repository = mockk<RepoAdminRepository>()
            val vm = CreateRepoViewModel(repository)

            vm.onNameChange("bad name")
            vm.submit()

            assertEquals(RepoNameError.INVALID_CHARS, vm.uiState.value.nameError)
            coVerify(exactly = 0) { repository.createRepository(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun submit_success_setsCreatedRef() =
        runTest {
            val vm = viewModel()

            vm.onNameChange("new-repo")
            vm.onDescriptionChange("  ")
            vm.onPrivateChange(true)
            vm.onAutoInitChange(false)
            vm.submit()

            val state = vm.uiState.value
            assertEquals(RepoRef("octocat", "new-repo"), state.created)
            assertFalse(state.isSubmitting)
            assertNull(state.nameError)
        }

    @Test
    fun submit_success_passesTrimmedFields() =
        runTest {
            val repository =
                mockk<RepoAdminRepository> {
                    coEvery { createRepository(any(), any(), any(), any(), any(), any()) } returns Result.success(created)
                }
            val vm = CreateRepoViewModel(repository)

            vm.onNameChange("  new-repo  ")
            vm.onDescriptionChange("  demo  ")
            vm.onPrivateChange(true)
            vm.onAutoInitChange(true)
            vm.submit()

            coVerify(exactly = 1) {
                repository.createRepository(
                    name = eq("new-repo"),
                    description = eq("demo"),
                    isPrivate = eq(true),
                    autoInit = eq(true),
                    gitignoreTemplate = isNull(),
                    licenseTemplate = isNull(),
                )
            }
        }

    @Test
    fun submit_blankDescription_passesNull() =
        runTest {
            val repository =
                mockk<RepoAdminRepository> {
                    coEvery { createRepository(any(), any(), any(), any(), any(), any()) } returns Result.success(created)
                }
            val vm = CreateRepoViewModel(repository)

            vm.onNameChange("new-repo")
            vm.onDescriptionChange("   ")
            vm.submit()

            coVerify(exactly = 1) {
                repository.createRepository(
                    eq("new-repo"),
                    isNull(),
                    eq(false),
                    eq(true),
                    isNull(),
                    isNull(),
                )
            }
        }

    @Test
    fun submit_422Response_emitsNameTakenEvent() =
        runTest {
            val vm = viewModel(Result.failure(GitHubRequestException(GitHubError.Validation, null)))

            vm.onNameChange("taken")
            vm.events.test {
                vm.submit()

                assertEquals(CreateRepoEvent.NameTaken, awaitItem())
            }
            assertNull(vm.uiState.value.created)
        }

    @Test
    fun submit_403Response_emitsPermissionDeniedEvent() =
        runTest {
            val vm = viewModel(Result.failure(GitHubRequestException(GitHubError.Forbidden, null)))

            vm.onNameChange("repo")
            vm.events.test {
                vm.submit()

                assertEquals(CreateRepoEvent.PermissionDenied, awaitItem())
            }
        }

    @Test
    fun submit_networkError_emitsFailedEvent() =
        runTest {
            val vm = viewModel(Result.failure(IOException("offline")))

            vm.onNameChange("repo")
            vm.events.test {
                vm.submit()

                assertEquals(CreateRepoEvent.Failed, awaitItem())
            }
        }

    @Test
    fun submit_whileSubmitting_ignoresSecondCall() =
        runTest {
            // 桩内挂起：第一次提交停在请求中（isSubmitting=true），第二次必须被忽略
            val repository =
                mockk<RepoAdminRepository> {
                    coEvery { createRepository(any(), any(), any(), any(), any(), any()) } coAnswers
                        {
                            delay(50)
                            Result.success(created)
                        }
                }
            val vm = CreateRepoViewModel(repository)
            vm.onNameChange("repo")

            vm.submit()
            vm.submit()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.createRepository(any(), any(), any(), any(), any(), any()) }
        }
}
