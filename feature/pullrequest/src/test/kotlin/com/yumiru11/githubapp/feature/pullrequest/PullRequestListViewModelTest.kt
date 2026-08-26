package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.data.RepositoryControl
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * PullRequestListViewModel 单测（纯 JVM，MockK 桩 Repository，MainDispatcherRule 替换 Main）。
 *
 * 覆盖：成功 → Success(OPEN)、setFilter(CLOSED/ALL) 重建分页流、owner/repo 自 SavedStateHandle、
 * 错误映射（404→NOT_FOUND / HTTP→NETWORK / IO→NETWORK / 未知→UNKNOWN）、retry 恢复与守卫。
 */
class PullRequestListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun repository(): PullRequestRepository =
        mockk<PullRequestRepository> {
            every { pulls(any(), any(), any()) } returns flowOf(PagingData.empty())
            // T23：仓库写控制（创建 PR 入口显隐；默认 UNKNOWN → 隐藏）
            coEvery { repositoryControl(any(), any()) } returns RepositoryControl()
        }

    private fun savedState(
        owner: String = "octocat",
        repo: String = "Hello-World",
    ): SavedStateHandle = SavedStateHandle(mapOf("owner" to owner, "repo" to repo))

    @Test
    fun load_success_emitsSuccess() =
        runTest {
            val viewModel = PullRequestListViewModel(savedState(), repository())

            assertTrue(viewModel.uiState.value is PullRequestListUiState.Success)
            assertEquals(PullRequestFilter.OPEN, viewModel.filter.value)
        }

    @Test
    fun load_writePermission_setsCanCreatePullRequest() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    every { pulls(any(), any(), any()) } returns flowOf(PagingData.empty())
                    coEvery { repositoryControl(any(), any()) } returns
                        RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
                }

            val viewModel = PullRequestListViewModel(savedState(), repo)

            assertTrue((viewModel.uiState.value as PullRequestListUiState.Success).canCreatePullRequest)
        }

    @Test
    fun load_noPermission_setsCanCreatePullRequestFalse() =
        runTest {
            val viewModel = PullRequestListViewModel(savedState(), repository())

            assertFalse((viewModel.uiState.value as PullRequestListUiState.Success).canCreatePullRequest)
        }

    @Test
    fun setFilter_closed_updatesFilterAndRebuildsFlow() =
        runTest {
            val viewModel = PullRequestListViewModel(savedState(), repository())

            viewModel.setFilter(PullRequestFilter.CLOSED)

            assertEquals(PullRequestFilter.CLOSED, viewModel.filter.value)
            assertTrue(viewModel.uiState.value is PullRequestListUiState.Success)
        }

    @Test
    fun setFilter_all_updatesFilter() =
        runTest {
            val viewModel = PullRequestListViewModel(savedState(), repository())

            viewModel.setFilter(PullRequestFilter.ALL)

            assertEquals(PullRequestFilter.ALL, viewModel.filter.value)
        }

    @Test
    fun setFilter_sameFilter_doesNotRebuild() =
        runTest {
            val viewModel = PullRequestListViewModel(savedState(), repository())

            viewModel.setFilter(PullRequestFilter.OPEN)

            // 同过滤不重建（幂等），filter 保持 OPEN
            assertEquals(PullRequestFilter.OPEN, viewModel.filter.value)
        }

    @Test
    fun savedStateHandle_providesOwnerAndRepo() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    every { pulls("octocat", "Hello-World", PullRequestFilter.OPEN) } returns flowOf(PagingData.empty())
                }

            PullRequestListViewModel(savedState(), repo)
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
                    every { pulls(any(), any(), any()) } throws httpException
                }

            val viewModel = PullRequestListViewModel(savedState(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestListUiState.Error)
            assertEquals(PullRequestErrorType.NOT_FOUND, (state as PullRequestListUiState.Error).errorType)
        }

    @Test
    fun load_httpError_emitsErrorNetwork() =
        runTest {
            val httpException =
                mockk<HttpException> {
                    every { code() } returns 500
                }
            val repo =
                mockk<PullRequestRepository> {
                    every { pulls(any(), any(), any()) } throws httpException
                    coEvery { repositoryControl(any(), any()) } returns RepositoryControl()
                }

            val viewModel = PullRequestListViewModel(savedState(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestListUiState.Error)
            assertEquals(PullRequestErrorType.NETWORK, (state as PullRequestListUiState.Error).errorType)
        }

    @Test
    fun load_ioError_emitsErrorNetwork() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    every { pulls(any(), any(), any()) } throws IOException("boom")
                }

            val viewModel = PullRequestListViewModel(savedState(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestListUiState.Error)
            assertEquals(PullRequestErrorType.NETWORK, (state as PullRequestListUiState.Error).errorType)
        }

    @Test
    fun load_unknownError_emitsErrorUnknown() =
        runTest {
            val repo =
                mockk<PullRequestRepository> {
                    every { pulls(any(), any(), any()) } throws IllegalStateException("boom")
                }

            val viewModel = PullRequestListViewModel(savedState(), repo)

            val state = viewModel.uiState.value
            assertTrue(state is PullRequestListUiState.Error)
            assertEquals(PullRequestErrorType.UNKNOWN, (state as PullRequestListUiState.Error).errorType)
        }

    @Test
    fun retry_afterError_recoversToSuccess() =
        runTest {
            val httpException =
                mockk<HttpException> {
                    every { code() } returns 500
                }
            val repo =
                mockk<PullRequestRepository> {
                    every { pulls(any(), any(), any()) } throws httpException
                    coEvery { repositoryControl(any(), any()) } returns RepositoryControl()
                }
            val viewModel = PullRequestListViewModel(savedState(), repo)
            assertTrue(viewModel.uiState.value is PullRequestListUiState.Error)

            // 恢复桩：重试后成功
            every { repo.pulls(any(), any(), any()) } returns flowOf(PagingData.empty())

            viewModel.retry()

            assertTrue(viewModel.uiState.value is PullRequestListUiState.Success)
        }

    @Test
    fun retry_whenSuccess_doesNothing() =
        runTest {
            val viewModel = PullRequestListViewModel(savedState(), repository())
            assertTrue(viewModel.uiState.value is PullRequestListUiState.Success)

            viewModel.retry()

            assertTrue(viewModel.uiState.value is PullRequestListUiState.Success)
        }
}
