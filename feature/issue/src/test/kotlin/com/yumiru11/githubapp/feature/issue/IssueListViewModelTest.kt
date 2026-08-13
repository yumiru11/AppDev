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
import java.io.IOException

/**
 * IssueListViewModel 单测（纯 JVM，MockK 桩 Repository，MainDispatcherRule 替换 Main）。
 *
 * 覆盖：成功 → Success(OPEN)、setFilter(CLOSED) 重建分页流、owner/repo 自 SavedStateHandle、错误映射。
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
}
