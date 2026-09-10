package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
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
 * [ReleaseCreateViewModel] 单测（L05）。
 *
 * 覆盖：默认分支预填、Tag 空提交拦截、成功 → created（Tag）、422 → ValidationFailed、
 * 403 → PermissionDenied、网络 → Failed、字段裁剪、提交中防重入。
 */
class ReleaseCreateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "ref" to "main"))

    private fun viewModel(result: Result<Release> = Result.success(Release(id = 7, tagName = "v1.0.0"))): ReleaseCreateViewModel {
        val repository =
            mockk<RepoManagementRepository> {
                coEvery { createRelease(any(), any(), any()) } returns result
            }
        return ReleaseCreateViewModel(savedStateHandle, repository)
    }

    @Test
    fun init_prefillsTargetCommitishFromSavedState() {
        val vm = viewModel()

        assertEquals("main", vm.uiState.value.targetCommitish)
    }

    @Test
    fun submit_blankTag_marksErrorAndSkipsRequest() =
        runTest {
            val repository = mockk<RepoManagementRepository>()
            val vm = ReleaseCreateViewModel(savedStateHandle, repository)

            vm.submit()

            assertTrue(vm.uiState.value.tagError)
            coVerify(exactly = 0) { repository.createRelease(any(), any(), any()) }
        }

    @Test
    fun onTagChange_nonBlank_clearsTagError() {
        val vm = viewModel()

        vm.submit()
        vm.onTagChange("v1")

        assertFalse(vm.uiState.value.tagError)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun submit_success_setsCreatedTag() =
        runTest {
            val vm = viewModel()

            vm.onTagChange("v1.0.0")
            vm.onNameChange("1.0.0")
            vm.onBodyChange("notes")
            vm.onDraftChange(true)
            vm.onPrereleaseChange(true)
            vm.submit()

            assertEquals("v1.0.0", vm.uiState.value.created)
            assertFalse(vm.uiState.value.isSubmitting)
        }

    @Test
    fun submit_success_passesTrimmedFieldsAndSwitches() =
        runTest {
            val repository =
                mockk<RepoManagementRepository> {
                    coEvery { createRelease(any(), any(), any()) } returns
                        Result.success(Release(id = 1, tagName = "v2"))
                }
            val vm = ReleaseCreateViewModel(savedStateHandle, repository)

            vm.onTagChange("  v2  ")
            vm.onTargetChange("release/2.x")
            vm.onNameChange("  two  ")
            vm.onBodyChange("  body  ")
            vm.onDraftChange(true)
            vm.onPrereleaseChange(true)
            vm.submit()

            coVerify(exactly = 1) {
                repository.createRelease(
                    eq("octocat"),
                    eq("Hello-World"),
                    eq(
                        NewRelease(
                            tagName = "v2",
                            targetCommitish = "release/2.x",
                            name = "two",
                            body = "body",
                            draft = true,
                            prerelease = true,
                        ),
                    ),
                )
            }
        }

    @Test
    fun submit_blankOptionalFields_passesNulls() =
        runTest {
            val repository =
                mockk<RepoManagementRepository> {
                    coEvery { createRelease(any(), any(), any()) } returns
                        Result.success(Release(id = 1, tagName = "v2"))
                }
            val vm = ReleaseCreateViewModel(SavedStateHandle(mapOf("owner" to "o", "repo" to "r")), repository)

            vm.onTagChange("v2")
            vm.submit()

            coVerify(exactly = 1) {
                repository.createRelease(
                    eq("o"),
                    eq("r"),
                    eq(NewRelease(tagName = "v2")),
                )
            }
        }

    @Test
    fun submit_422Response_emitsValidationFailed() =
        runTest {
            val vm = viewModel(Result.failure(GitHubRequestException(GitHubError.Validation, null)))

            vm.onTagChange("bad tag")
            vm.events.test {
                vm.submit()

                assertEquals(ReleaseCreateEvent.ValidationFailed, awaitItem())
            }
            assertNull(vm.uiState.value.created)
        }

    @Test
    fun submit_403Response_emitsPermissionDenied() =
        runTest {
            val vm = viewModel(Result.failure(GitHubRequestException(GitHubError.Forbidden, null)))

            vm.onTagChange("v1")
            vm.events.test {
                vm.submit()

                assertEquals(ReleaseCreateEvent.PermissionDenied, awaitItem())
            }
        }

    @Test
    fun submit_ioError_emitsFailed() =
        runTest {
            val vm = viewModel(Result.failure(IOException("offline")))

            vm.onTagChange("v1")
            vm.events.test {
                vm.submit()

                assertEquals(ReleaseCreateEvent.Failed, awaitItem())
            }
        }

    @Test
    fun submit_whileSubmitting_ignoresSecondCall() =
        runTest {
            // 桩内挂起：第一次提交停在请求中（isSubmitting=true），第二次必须被忽略
            val repository =
                mockk<RepoManagementRepository> {
                    coEvery { createRelease(any(), any(), any()) } coAnswers
                        {
                            delay(50)
                            Result.success(Release(id = 1, tagName = "v1"))
                        }
                }
            val vm = ReleaseCreateViewModel(savedStateHandle, repository)
            vm.onTagChange("v1")

            vm.submit()
            vm.submit()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.createRelease(any(), any(), any()) }
        }
}
