package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * [RepoIssueListViewModel] 单测（2026-10 UI 重构：仓库内议题/PR 分区共用一次 /issues 拉取）。
 *
 * 覆盖：一次请求拆成议题与 PR 两个列表、分页触底追加、首载幂等、失败 → Error 态与重试、
 * 未设 owner/repo 时重试短路（防误打空路径）。
 */
class RepoIssueListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun dto(
        number: Int,
        isPullRequest: Boolean = false,
    ): IssueDto =
        IssueDto(
            id = number.toLong(),
            number = number,
            title = "Item #$number",
            state = "open",
            comments = number,
            pullRequest = if (isPullRequest) JsonObject(emptyMap()) else null,
        )

    private fun viewModel(
        items: List<IssueDto>,
        fail: Boolean = false,
    ): Pair<RepoIssueListViewModel, IssueApi> {
        val api = mockk<IssueApi>(relaxed = true)
        if (fail) {
            coEvery { api.listIssues(any(), any(), any(), any(), any()) } throws IOException("boom")
        } else {
            coEvery { api.listIssues(any(), any(), any(), any(), any()) } returns items
        }
        return RepoIssueListViewModel(api) to api
    }

    @Test
    fun ensureLoaded_splitsIssuesAndPullsFromSingleRequest() =
        runTest {
            val (vm, api) = viewModel(listOf(dto(1), dto(2, isPullRequest = true)))
            vm.ensureLoaded("octocat", "Hello-World")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, state.issues.size)
            assertEquals(1, state.pulls.size)
            assertTrue(state.loaded)
            coVerify(exactly = 1) { api.listIssues(any(), any(), any(), any(), any()) }
        }

    @Test
    fun ensureLoaded_calledTwice_requestsOnce() =
        runTest {
            val (vm, api) = viewModel(listOf(dto(1)))
            vm.ensureLoaded("octocat", "Hello-World")
            advanceUntilIdle()
            vm.ensureLoaded("octocat", "Hello-World")
            advanceUntilIdle()
            coVerify(exactly = 1) { api.listIssues(any(), any(), any(), any(), any()) }
        }

    @Test
    fun fullFirstPage_marksHasMore_andLoadMoreAppends() =
        runTest {
            val page = (1..30).map { dto(it) }
            val (vm, _) = viewModel(page)
            vm.ensureLoaded("octocat", "Hello-World")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.hasMore)

            vm.loadMore()
            advanceUntilIdle()
            assertEquals(60, vm.uiState.value.items.size)
        }

    @Test
    fun shortFirstPage_marksNoMore_andLoadMoreIsNoop() =
        runTest {
            val (vm, api) = viewModel(listOf(dto(1)))
            vm.ensureLoaded("octocat", "Hello-World")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.hasMore)

            vm.loadMore()
            advanceUntilIdle()
            coVerify(exactly = 1) { api.listIssues(any(), any(), any(), any(), any()) }
        }

    @Test
    fun loadFailure_setsErrorState_thenRetryRecovers() =
        runTest {
            val (vm, api) = viewModel(emptyList(), fail = true)
            vm.ensureLoaded("octocat", "Hello-World")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.error)
            assertFalse(vm.uiState.value.loading)

            coEvery { api.listIssues(any(), any(), any(), any(), any()) } returns listOf(dto(9))
            vm.retry()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.error)
            assertEquals(1, vm.uiState.value.issues.size)
        }

    @Test
    fun retryBeforeEnsureLoaded_isIgnored() =
        runTest {
            val (vm, api) = viewModel(listOf(dto(1)))
            vm.retry()
            advanceUntilIdle()
            coVerify(exactly = 0) { api.listIssues(any(), any(), any(), any(), any()) }
        }
}
