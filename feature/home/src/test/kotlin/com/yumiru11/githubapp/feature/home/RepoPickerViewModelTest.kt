package com.yumiru11.githubapp.feature.home

import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.home.data.UserReposRepository
import com.yumiru11.githubapp.feature.home.model.RepoOption
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertIs

/**
 * [RepoPickerViewModel] 单测（#89，纯 JVM MockK 桩仓库）：
 * 成功 → Ready、IO 失败 → NETWORK、未知失败 → UNKNOWN、重试恢复。
 */
class RepoPickerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_success_emitsReadyWithRepos() =
        runTest {
            val repos = listOf(RepoOption("octocat", "hello-world", "My first repo", false))
            val repository =
                mockk<UserReposRepository> {
                    coEvery { currentUserRepos() } returns repos
                }
            val viewModel = RepoPickerViewModel(repository)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertIs<RepoPickerUiState.Ready>(state)
            assertEquals(repos, state.repos)
        }

    @Test
    fun load_ioException_mapsNetworkError() =
        runTest {
            val repository =
                mockk<UserReposRepository> {
                    coEvery { currentUserRepos() } throws IOException()
                }
            val viewModel = RepoPickerViewModel(repository)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertIs<RepoPickerUiState.Error>(state)
            assertEquals(HomeErrorType.NETWORK, state.errorType)
        }

    @Test
    fun load_unexpectedError_mapsUnknownError() =
        runTest {
            var fail = true
            val repository =
                mockk<UserReposRepository> {
                    coEvery { currentUserRepos() } answers { if (fail) error("boom") else emptyList() }
                }
            val viewModel = RepoPickerViewModel(repository)
            advanceUntilIdle()
            assertIs<RepoPickerUiState.Error>(viewModel.uiState.value)

            fail = false
            viewModel.retry()
            advanceUntilIdle()
            val recovered = viewModel.uiState.value
            assertIs<RepoPickerUiState.Ready>(recovered)
            assertTrue(recovered.repos.isEmpty())
        }
}
