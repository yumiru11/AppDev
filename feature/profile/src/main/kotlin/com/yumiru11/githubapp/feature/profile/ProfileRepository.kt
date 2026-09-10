package com.yumiru11.githubapp.feature.profile

import androidx.paging.PagingSource
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.api.GistApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.feature.profile.model.GistItem
import com.yumiru11.githubapp.feature.profile.paging.FollowersPagingSource
import com.yumiru11.githubapp.feature.profile.paging.FollowingPagingSource
import com.yumiru11.githubapp.feature.profile.paging.GistPagingSource
import com.yumiru11.githubapp.feature.profile.paging.RepositoriesPagingSource
import com.yumiru11.githubapp.feature.profile.paging.StarredPagingSource
import retrofit2.HttpException
import retrofit2.Response
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
        private val gistApi: GistApi,
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

        /**
         * 关注状态：**204 = 已关注 / 404 = 未关注**（GitHub 用状态码编码布尔语义）。
         *
         * 其余状态（401/403/5xx）抛 [HttpException] 交上层——既不能当「未关注」静默吞掉
         * （UI 会显示错误的按钮文案），也不能当「已关注」。
         */
        suspend fun isFollowing(login: String): Boolean {
            val response = userApi.isFollowing(login)
            return when (response.code()) {
                HTTP_NO_CONTENT -> true
                HTTP_NOT_FOUND -> false
                else -> throw HttpException(response)
            }
        }

        /** 关注（204 = 成功；其余状态抛 [HttpException] 交上层回滚乐观更新） */
        suspend fun follow(login: String) {
            requireSuccess(userApi.follow(login))
        }

        /** 取关（204 = 成功；其余状态抛 [HttpException] 交上层回滚乐观更新） */
        suspend fun unfollow(login: String) {
            requireSuccess(userApi.unfollow(login))
        }

        /** Gist 列表分页数据源（L11；本人/他人主页共用公开端点） */
        fun gists(username: String): PagingSource<Int, GistItem> = GistPagingSource(gistApi = gistApi, username = username)

        fun repositories(login: String?): PagingSource<Int, Repository> = RepositoriesPagingSource(userApi = userApi, login = login)

        fun starred(login: String?): PagingSource<Int, Repository> = StarredPagingSource(userApi = userApi, login = login)

        fun followers(login: String?): PagingSource<Int, User> = FollowersPagingSource(userApi = userApi, login = login)

        fun following(login: String?): PagingSource<Int, User> = FollowingPagingSource(userApi = userApi, login = login)

        private fun requireSuccess(response: Response<Unit>) {
            if (!response.isSuccessful) throw HttpException(response)
        }

        private companion object {
            /** HTTP 204 No Content：GitHub 关注类端点的成功/已关注码 */
            const val HTTP_NO_CONTENT = 204

            /** HTTP 404 Not Found：/user/following/{u} 的「未关注」语义 */
            const val HTTP_NOT_FOUND = 404
        }
    }
