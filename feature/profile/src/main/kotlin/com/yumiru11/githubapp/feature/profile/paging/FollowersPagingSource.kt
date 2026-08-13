@file:Suppress("TooGenericExceptionCaught") // PagingSource.load 不允许抛异常，网络/IO/未知错误统一收敛为 LoadResult.Error

package com.yumiru11.githubapp.feature.profile.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.feature.profile.toUser

/**
 * 关注者分页数据源（REST page/per_page 整数分页）。
 *
 * [login] 为 null 时用当前认证用户端点（/user/followers），否则用公开端点（/users/{login}/followers）。
 */
class FollowersPagingSource
    constructor(
        private val userApi: UserApi,
        private val login: String? = null,
    ) : PagingSource<Int, User>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
            val page = params.key ?: 1
            return try {
                val users =
                    if (login == null) {
                        userApi.currentUserFollowers(perPage = params.loadSize, page = page)
                    } else {
                        userApi.userFollowers(login, perPage = params.loadSize, page = page)
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
