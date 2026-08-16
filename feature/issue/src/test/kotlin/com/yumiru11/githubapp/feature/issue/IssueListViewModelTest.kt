package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * IssueListViewModel 单测（纯 JVM，MockK 桩 Repository，MainDispatcherRule 替换 Main）。
 *
 * 覆盖：成功 → Success(OPEN)、setFilter(CLOSED) 重建分页流、owner/repo 自 SavedStateHandle、
 * 错误映射（404→NOT_FOUND / HTTP→NETWORK / IO→NETWORK / 未知→UNKNOWN）、retry 恢复与守卫、
 * 过滤切换后错误态重建分页流（分页数据流构造期失败）。
 */
class IssueListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun repository(): IssueRepository =
        mockk<IssueRepository> {
            every { issues(any(), any(), any()) } returns flowOf(PagingData.empty())
        }

    private fun savedState(
        owner: String = "octocat",
        repo: String = "Hello-World",
    ): SavedStateHandle = SavedStateHandle(mapOf("owner" to owner, "repo" to repo))

    @Test
    fun load_success_emitsSuccess() =
        runTest {
            val viewModel = IssueListViewModel(savedState(), repository())

            assertTrue(viewModel.uiState.value is IssueListUiState.Success)
            assertEquals(IssueFilter.OPEN, viewModel.filter.value)
        }

    @Test
    fun setFilter_closed_updatesFilter() =
        runTest {
            val viewModel = IssueListViewModel(savedState(), repository())

            viewModel.setFilter(IssueFilter.CLOSED)

            assertEquals(IssueFilter.CLOSED, viewModel.filter.value)
            assertTrue(viewModel.uiState.value is IssueListUiState.Success)
        }

    @Test
    fun savedStateHandle_providesOwnerAndRepo() =
        runTest {
            val repo =
                mockk<IssueRepository> {
                    every { issues("octocat", "Hello-World", IssueFilter.OPEN) } returns flowOf(PagingData.empty())
                }

            IssueListViewModel(savedState(), repo)

            verify { repo.issues("octocat", "Hello-World", IssueFilter.OPEN) }
        }

    @Test
    fun load_repositoryThrowsNetworkError_emitsErrorNetwork() =
        runTest {
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), any()) } throws IOException("network down")
                }

            val viewModel = IssueListViewModel(savedState(), repo)

            assertEquals(IssueListUiState.Error(IssueErrorType.NETWORK), viewModel.uiState.value)
        }

    @Test
    fun load_http404_emitsErrorNotFound() =
        runTest {
            val httpException =
                mockk<HttpException> {
                    every { code() } returns 404
                }
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), any()) } throws httpException
                }

            val viewModel = IssueListViewModel(savedState(), repo)

            assertEquals(IssueListUiState.Error(IssueErrorType.NOT_FOUND), viewModel.uiState.value)
        }

    @Test
    fun load_http500_emitsErrorNetwork() =
        runTest {
            val httpException =
                mockk<HttpException> {
                    every { code() } returns 500
                }
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), any()) } throws httpException
                }

            val viewModel = IssueListViewModel(savedState(), repo)

            assertEquals(IssueListUiState.Error(IssueErrorType.NETWORK), viewModel.uiState.value)
        }

    @Test
    fun load_unknownException_emitsErrorUnknown() =
        runTest {
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), any()) } throws IllegalStateException("boom")
                }

            val viewModel = IssueListViewModel(savedState(), repo)

            assertEquals(IssueListUiState.Error(IssueErrorType.UNKNOWN), viewModel.uiState.value)
        }

    @Test
    fun retry_afterError_reloadsAndSucceeds() =
        runTest {
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), any()) } throws IOException("network down")
                }
            val viewModel = IssueListViewModel(savedState(), repo)
            assertEquals(IssueListUiState.Error(IssueErrorType.NETWORK), viewModel.uiState.value)

            every { repo.issues(any(), any(), any()) } returns flowOf(PagingData.empty())
            viewModel.retry()

            assertTrue(viewModel.uiState.value is IssueListUiState.Success)
            verify(exactly = 2) { repo.issues(any(), any(), any()) }
        }

    @Test
    fun retry_inSuccessState_doesNotReload() =
        runTest {
            val repo = repository()
            val viewModel = IssueListViewModel(savedState(), repo)

            viewModel.retry()

            verify(exactly = 1) { repo.issues(any(), any(), any()) }
        }

    @Test
    fun setFilter_afterError_reloadsAndSucceeds() =
        runTest {
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), IssueFilter.OPEN) } throws IOException("network down")
                }
            val viewModel = IssueListViewModel(savedState(), repo)
            assertEquals(IssueListUiState.Error(IssueErrorType.NETWORK), viewModel.uiState.value)

            every { repo.issues(any(), any(), IssueFilter.CLOSED) } returns flowOf(PagingData.empty())
            viewModel.setFilter(IssueFilter.CLOSED)

            assertEquals(IssueFilter.CLOSED, viewModel.filter.value)
            assertTrue(viewModel.uiState.value is IssueListUiState.Success)
            verify { repo.issues("octocat", "Hello-World", IssueFilter.CLOSED) }
        }

    @Test
    fun setFilter_newFilterFlowThrows_emitsError() =
        runTest {
            val repo =
                mockk<IssueRepository> {
                    every { issues(any(), any(), IssueFilter.OPEN) } returns flowOf(PagingData.empty())
                    every { issues(any(), any(), IssueFilter.CLOSED) } throws IOException("network down")
                }
            val viewModel = IssueListViewModel(savedState(), repo)
            assertTrue(viewModel.uiState.value is IssueListUiState.Success)

            viewModel.setFilter(IssueFilter.CLOSED)

            assertEquals(IssueFilter.CLOSED, viewModel.filter.value)
            assertEquals(IssueListUiState.Error(IssueErrorType.NETWORK), viewModel.uiState.value)
        }
}
