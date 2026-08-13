@file:Suppress("TooGenericExceptionCaught") // PagingSource.load 不允许抛异常，网络/IO/未知错误统一收敛为 LoadResult.Error

package com.yumiru11.githubapp.feature.profile.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.feature.profile.toUser

/**
 * 关注中分页数据源（REST page/per_page 整数分页）。
 *
 * [login] 为 null 时用当前认证用户端点（/user/following），否则用公开端点（/users/{login}/following）。
 */
class FollowingPagingSource
    constructor(
        private val userApi: UserApi,
        private val login: String? = null,
    ) : PagingSource<Int, User>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
            val page = params.key ?: 1
            return try {
                val users =
                    if (login == null) {
                        userApi.currentUserFollowing(perPage = params.loadSize, page = page)
                    } else {
                        userApi.userFollowing(login, perPage = params.loadSize, page = page)
                    }
                LoadResult.Page(
                    data = users.map { it.toUser() },
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (users.size == params.loadSize) page + 1 else null,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, User>): Int? = null
    }
