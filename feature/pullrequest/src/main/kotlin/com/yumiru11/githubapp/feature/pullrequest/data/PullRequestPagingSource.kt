package com.yumiru11.githubapp.feature.pullrequest.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import retrofit2.HttpException
import java.io.IOException

/**
 * PR 列表分页数据源：按 [owner]/[repo]/[filter] 请求 GET /repos/{owner}/{repo}/pulls
 * 并映射为领域模型。
 *
 * 下一页判定：仅当返回条数恰满本页（pulls.size == params.loadSize）才推进；
 * 末页不满一页即视为无更多数据，避免末尾多请求一次空页。
 * 错误语义：网络/HTTP 一律转 [LoadResult.Error]，UI 层 loadState 驱动错误态与重试（同 IssuePagingSource 先例）。
 */
class PullRequestPagingSource(
    private val pullRequestApi: PullRequestApi,
    private val owner: String,
    private val repo: String,
    private val filter: PullRequestFilter,
) : PagingSource<Int, PullRequest>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PullRequest> =
        try {
            val page = params.key ?: STARTING_PAGE
            val pulls =
                pullRequestApi.listPullRequests(
                    owner = owner,
                    repo = repo,
                    state = filter.toRaw(),
                    page = page,
                    perPage = params.loadSize,
                )
            LoadResult.Page(
                data = pulls.map { it.toDomain() },
                prevKey = if (page > STARTING_PAGE) page - 1 else null,
                nextKey = if (pulls.size == params.loadSize) page + 1 else null,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Int, PullRequest>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { closestPage ->
                closestPage.prevKey?.plus(1) ?: closestPage.nextKey?.minus(1)
            }
        }

    private companion object {
        const val STARTING_PAGE = 1
    }
}
