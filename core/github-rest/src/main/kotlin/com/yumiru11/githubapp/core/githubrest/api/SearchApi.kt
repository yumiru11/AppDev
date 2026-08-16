package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CodeSearchItemDto
import com.yumiru11.githubapp.core.githubrest.model.SearchCodeResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchIssuesResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchRepositoriesResponse
import com.yumiru11.githubapp.core.githubrest.model.SearchUsersResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GitHub 搜索 REST 接口（搜索 REST-only，plan.md §3.2 / §9.3）。
 *
 * 四个端点对应四个结果 Tab；PR 搜索复用 [searchIssues] + `is:pr` qualifier
 * （GitHub 无独立 PR 搜索端点，PR 条目由 issue 搜索的 `pull_request` 字段标识）。
 *
 * 限流注意：搜索 API 限流远严于常规 REST（未认证 10 次/分、认证 30 次/分），
 * 429 由上层 [com.yumiru11.githubapp.core.githubdata.error.asGitHubError]
 * 归一化为 RateLimited → UI 友好提示。
 *
 * 代码搜索额外限制：必须登录（未认证直接 401）；仅索引默认分支；
 * 每仓库最多展示前 100 个匹配文件。
 */
interface SearchApi {
    /** GET /search/repositories：仓库搜索 */
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): SearchRepositoriesResponse

    /** GET /search/users：用户搜索 */
    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): SearchUsersResponse

    /** GET /search/issues：Issue 搜索（同时返回 PR，靠 `pull_request` 字段区分） */
    @GET("search/issues")
    suspend fun searchIssues(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): SearchIssuesResponse

    /** GET /search/code：代码搜索（需登录） */
    @GET("search/code")
    suspend fun searchCode(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): SearchCodeResponse
}
