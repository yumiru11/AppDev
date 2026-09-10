package com.yumiru11.githubapp.feature.profile

import androidx.paging.PagingSource
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.api.GistApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.http.GitHubLinkHeader
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
        suspend fun getProfile(login: String?): User {
            val user =
                if (login == null) {
                    userApi.currentUser().toUser()
                } else {
                    userApi.getUser(login).toUser()
                }
            // Star 总数（#166 / UI21）：REST 资料端点不返回，单独用 per_page=1 探一次
            // Link 头推总数。探测失败不影响资料头 —— 统计行少一项比整页报错好得多。
            return user.copy(starredCount = starredCountOrNull(login))
        }

        /**
         * 探测 Star 总数；任何失败（网络/权限/解析）都返回 null，由 UI 决定是否隐藏该项。
         *
         * 用 retrofit2.Response 承接是为了读 Link 响应头（总数信息只在头里）。
         *
         * 刻意不加协程 withTimeout：时长上限由 OkHttp 客户端自身的 connect/read 超时约束
         * （见 GitHubRestClient.createOkHttpClient）。加一层协程超时反而有害 —— runTest 用虚拟时钟，
         * withTimeout 会在真实网络挂起时被虚拟时间"立刻"触发，让所有基于 runTest 的资料头测试
         * 拿到 null（本 PR 实测踩到）。
         */
        private suspend fun starredCountOrNull(login: String?): Int? = runCatching { probeStarredCount(login) }.getOrNull()

        private suspend fun probeStarredCount(login: String?): Int? {
            val response =
                if (login == null) {
                    userApi.currentUserStarredCountProbe(perPage = COUNT_PROBE_PER_PAGE)
                } else {
                    userApi.userStarredCountProbe(login = login, perPage = COUNT_PROBE_PER_PAGE)
                }
            // 非 2xx 必须返回 null（"未知"）而不是 0：Retrofit 的 Response 对 4xx/5xx 不抛异常，
            // 若继续往下走会得到 currentPageSize = 0，UI 就会把"探测失败"显示成"0 stars"。
            // （本 PR 实测：500 探测曾让统计行显示 0。）
            if (!response.isSuccessful) return null
            return GitHubLinkHeader.totalCount(
                linkHeader = response.headers()["Link"],
                perPage = COUNT_PROBE_PER_PAGE,
                currentPageSize = response.body()?.size ?: 0,
            )
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

            /** Star 总数探测用 per_page=1：Link 头的 last page 即总数，无需拉数据 */
            const val COUNT_PROBE_PER_PAGE = 1

            /** HTTP 404 Not Found：/user/following/{u} 的「未关注」语义 */
            const val HTTP_NOT_FOUND = 404
        }
    }
