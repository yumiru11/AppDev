package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * [CommitDetailViewModel] 单测（L09）。
 *
 * 覆盖：加载成功 → Success（文件列表）、404 → NOT_FOUND、403 → FORBIDDEN、网络 → NETWORK、
 * 点击文件 → 解析 diff、返回 → 清空选中、retry 重新加载。
 */
class CommitDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "sha" to "abc123def456"))

    private val commit =
        CommitDetail(
            sha = "abc123def456",
            message = "fix: x",
            additions = 2,
            deletions = 1,
            files =
                listOf(
                    CommitFile(
                        filename = "src/Main.kt",
                        status = CommitFileStatus.MODIFIED,
                        additions = 2,
                        deletions = 1,
                        patch = "@@ -1,1 +1,2 @@\n-old\n+new\n+extra",
                    ),
                    CommitFile(filename = "logo.png", status = CommitFileStatus.ADDED, patch = null),
                ),
        )

    private fun viewModel(result: Result<CommitDetail> = Result.success(commit)): CommitDetailViewModel {
        val repository =
            mockk<CommitRepository> {
                coEvery { getCommit(any(), any(), any()) } returns result
            }
        return CommitDetailViewModel(savedStateHandle, repository)
    }

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    @Test
    fun load_success_emitsSuccessWithFiles() =
        runTest {
            val state = viewModel().uiState.value

            assertTrue(state is CommitDetailUiState.Success)
            val success = state as CommitDetailUiState.Success
            assertEquals(2, success.commit.files.size)
            assertNull(success.selectedFile)
            assertTrue(success.diffLines.isEmpty())
        }

    @Test
    fun load_notFound_emitsErrorNotFound() =
        runTest {
            val state = viewModel(Result.failure(httpException(404))).uiState.value

            assertEquals(CommitDetailUiState.Error(RepoErrorType.NOT_FOUND), state)
        }

    @Test
    fun load_forbidden_emitsErrorForbidden() =
        runTest {
            val state = viewModel(Result.failure(httpException(403))).uiState.value

            assertEquals(CommitDetailUiState.Error(RepoErrorType.FORBIDDEN), state)
        }

    @Test
    fun load_networkError_emitsErrorNetwork() =
        runTest {
            val state = viewModel(Result.failure(IOException("offline"))).uiState.value

            assertEquals(CommitDetailUiState.Error(RepoErrorType.NETWORK), state)
        }

    @Test
    fun selectFile_textFile_parsesPatchIntoDiffLines() =
        runTest {
            val vm = viewModel()
            val success = vm.uiState.value as CommitDetailUiState.Success

            vm.selectFile(success.commit.files[0])

            val selected = vm.uiState.value as CommitDetailUiState.Success
            assertEquals("src/Main.kt", selected.selectedFile?.filename)
            assertEquals(4, selected.diffLines.size)
            assertEquals(CommitDiffLineKind.HEADER, selected.diffLines[0].kind)
            assertEquals(CommitDiffLineKind.REMOVED, selected.diffLines[1].kind)
        }

    @Test
    fun selectFile_binaryFile_yieldsEmptyDiff() =
        runTest {
            val vm = viewModel()
            val success = vm.uiState.value as CommitDetailUiState.Success

            vm.selectFile(success.commit.files[1])

            val selected = vm.uiState.value as CommitDetailUiState.Success
            assertEquals("logo.png", selected.selectedFile?.filename)
            assertTrue(selected.diffLines.isEmpty())
        }

    @Test
    fun clearSelection_returnsToFileList() =
        runTest {
            val vm = viewModel()
            val success = vm.uiState.value as CommitDetailUiState.Success
            vm.selectFile(success.commit.files[0])

            vm.clearSelection()

            val cleared = vm.uiState.value as CommitDetailUiState.Success
            assertNull(cleared.selectedFile)
            assertTrue(cleared.diffLines.isEmpty())
        }

    @Test
    fun retry_afterError_reloadsCommit() =
        runTest {
            val repository =
                mockk<CommitRepository> {
                    coEvery { getCommit(any(), any(), any()) } returns
                        Result.failure(IOException("offline")) andThen
                        Result.success(commit)
                }
            val vm = CommitDetailViewModel(savedStateHandle, repository)
            assertTrue(vm.uiState.value is CommitDetailUiState.Error)

            vm.retry()

            assertTrue(vm.uiState.value is CommitDetailUiState.Success)
        }

    @Test
    fun selectFile_beforeLoad_completed_isIgnored() =
        runTest {
            val vm = viewModel(Result.failure(IOException("offline")))

            vm.selectFile(CommitFile(filename = "a.kt"))

            assertTrue(vm.uiState.value is CommitDetailUiState.Error)
        }
}
