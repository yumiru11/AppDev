@file:Suppress("TooGenericExceptionCaught") // PagingSource.load 不允许抛异常，网络/IO/未知错误统一收敛为 LoadResult.Error

package com.yumiru11.githubapp.feature.profile.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubrest.api.GistApi
import com.yumiru11.githubapp.feature.profile.model.GistItem
import com.yumiru11.githubapp.feature.profile.toGistItem

/**
 * Gist 分页数据源（L11，REST page/per_page 整数分页）。
 *
 * 下一页判定：返回条数达到 loadSize 才翻页（GitHub 尾页返回不足一页，
 * 空尾页自动终止 —— 同 RepositoriesPagingSource 口径）。
 */
class GistPagingSource
    constructor(
        private val gistApi: GistApi,
        private val username: String,
    ) : PagingSource<Int, GistItem>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GistItem> {
            val page = params.key ?: STARTING_PAGE
            return try {
                val gists = gistApi.userGists(username = username, perPage = params.loadSize, page = page)
                LoadResult.Page(
                    data = gists.map { it.toGistItem() },
                    prevKey = if (page > STARTING_PAGE) page - 1 else null,
                    nextKey = if (gists.size == params.loadSize) page + 1 else null,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, GistItem>): Int? = null

        private companion object {
            const val STARTING_PAGE = 1
        }
    }
