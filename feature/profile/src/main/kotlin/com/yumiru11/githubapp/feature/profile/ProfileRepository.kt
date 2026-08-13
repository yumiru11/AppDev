package com.yumiru11.githubapp.feature.profile

import androidx.paging.PagingSource
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.feature.profile.paging.FollowersPagingSource
import com.yumiru11.githubapp.feature.profile.paging.FollowingPagingSource
import com.yumiru11.githubapp.feature.profile.paging.RepositoriesPagingSource
import com.yumiru11.githubapp.feature.profile.paging.StarredPagingSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 个人页数据仓库（T20）：资料头 + 四个分页列表的数据源工厂。
 *
 * [login] 语义贯穿全类：null = 当前认证用户（/user* 私有端点），非 null = 公开用户
 * （/users/{login}* 公开端点）。PagingSource 由 ViewModel 包进 Pager，冷启动前不触网。
 */
@Singleton
class ProfileRepository
    @Inject
    constructor(
        private val userApi: UserApi,
    ) {
        /**
         * 获取用户资料头。
         *
         * @param login null = 当前认证用户（GET /user），否则 GET /users/{login}。
         * 未认证（Anonymous）时 UI 已拦截，不会走到本方法。
         */
        suspend fun getProfile(login: String?): User =
            if (login == null) {
                userApi.currentUser().toUser()
            } else {
                userApi.getUser(login).toUser()
            }

        fun repositories(login: String?): PagingSource<Int, Repository> = RepositoriesPagingSource(userApi = userApi, login = login)

        fun starred(login: String?): PagingSource<Int, Repository> = StarredPagingSource(userApi = userApi, login = login)

        fun followers(login: String?): PagingSource<Int, User> = FollowersPagingSource(userApi = userApi, login = login)

        fun following(login: String?): PagingSource<Int, User> = FollowingPagingSource(userApi = userApi, login = login)
    }
