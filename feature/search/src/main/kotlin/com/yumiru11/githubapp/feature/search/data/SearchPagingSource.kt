@file:Suppress("TooGenericExceptionCaught")
// 任意异常统一归一化（HttpException/IOException/未知），同 DefaultRepositoryRepository 先例

package com.yumiru11.githubapp.feature.search.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import kotlinx.coroutines.CancellationException

/**
 * 搜索结果分页数据源（通用：loader 按 Tab 绑定对应搜索端点）。
 *
 * 每 Tab 单独 Paging（plan.md §9.2：结果列表单独 Paging）：
 * loader 返回单页结果列表，items 非空即有下一页（项目既有分页约定）。
 *
 * 错误语义：任意异常（含 429）归一化为 [GitHubRequestException] 包进
 * [LoadResult.Error]——UI 层按 GitHubError 分类展示（限流友好提示）。
 */
class SearchPagingSource<T : Any>(
    private val loader: suspend (page: Int, perPage: Int) -> List<T>,
) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> =
        try {
            val page = params.key ?: STARTING_PAGE
            val items = loader(page, params.loadSize)
            LoadResult.Page(
                data = items,
                prevKey = if (page > STARTING_PAGE) page - 1 else null,
                nextKey = if (items.isNotEmpty()) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(GitHubRequestException(t.asGitHubError(), t))
        }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { closestPage ->
                closestPage.prevKey?.plus(1) ?: closestPage.nextKey?.minus(1)
            }
        }

    private companion object {
        const val STARTING_PAGE = 1
    }
}
