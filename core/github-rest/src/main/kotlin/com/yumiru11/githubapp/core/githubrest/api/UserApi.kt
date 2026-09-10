package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CreateRepositoryRequest
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 认证用户 REST 接口（写优先通道基础端点）。
 *
 * Retrofit 3 原生 suspend：非 2xx 自动抛 [retrofit2.HttpException]。
 */
interface UserApi {
    /** GET /user：当前认证用户资料 */
    @GET("user")
    suspend fun currentUser(): UserDto

    /** GET /users/{login}：任意用户公开资料 */
    @GET("users/{login}")
    suspend fun getUser(
        @Path("login") login: String,
    ): UserDto

    /** GET /user/repos：当前认证用户仓库（page/per_page 分页） */
    @GET("user/repos")
    suspend fun currentUserRepositories(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<RepositoryDto>

    /** GET /users/{login}/repos：任意用户公开仓库（page/per_page 分页） */
    @GET("users/{login}/repos")
    suspend fun userRepositories(
        @Path("login") login: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<RepositoryDto>

    /** GET /user/starred：当前认证用户 Starred 仓库（page/per_page 分页） */
    @GET("user/starred")
    suspend fun currentUserStarred(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<RepositoryDto>

    /** GET /users/{login}/starred：任意用户公开 Starred 仓库（page/per_page 分页） */
    @GET("users/{login}/starred")
    suspend fun userStarred(
        @Path("login") login: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<RepositoryDto>

    /**
     * GET /user/starred?per_page=1 —— **只为了拿 Link 响应头**（#166 / UI21）。
     *
     * 用 Response 承接而不是直接返回 List：总数信息只在响应头里，
     * 走 [com.yumiru11.githubapp.core.githubrest.http.GitHubLinkHeader] 解析。
     */
    @GET("user/starred")
    suspend fun currentUserStarredCountProbe(
        @Query("per_page") perPage: Int,
    ): Response<List<RepositoryDto>>

    /** GET /users/{login}/starred?per_page=1 —— 同上，任意用户公开 star 总数探测。 */
    @GET("users/{login}/starred")
    suspend fun userStarredCountProbe(
        @Path("login") login: String,
        @Query("per_page") perPage: Int,
    ): Response<List<RepositoryDto>>

    /** GET /user/followers：当前认证用户的关注者（page/per_page 分页） */
    @GET("user/followers")
    suspend fun currentUserFollowers(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<UserDto>

    /** GET /users/{login}/followers：任意用户的关注者（page/per_page 分页） */
    @GET("users/{login}/followers")
    suspend fun userFollowers(
        @Path("login") login: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<UserDto>

    /** GET /user/following：当前认证用户的关注中（page/per_page 分页） */
    @GET("user/following")
    suspend fun currentUserFollowing(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<UserDto>

    /** GET /users/{login}/following：任意用户的关注中（page/per_page 分页） */
    @GET("users/{login}/following")
    suspend fun userFollowing(
        @Path("login") login: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<UserDto>

    // ── 仓库创建（L04）───────────────────────────────────────────────────────

    /**
     * POST /user/repos：为当前认证用户创建仓库（L04，201 返回新仓库 DTO）。
     *
     * 422 命名/参数校验失败、403 无权限（如超出配额）、404 模板不存在，
     * 均抛 [retrofit2.HttpException]，由上层归一化为
     * [com.yumiru11.githubapp.core.githubdata.error.GitHubError]。
     */
    @POST("user/repos")
    suspend fun createRepository(
        @Body body: CreateRepositoryRequest,
    ): RepositoryDto

    // ── 关注写操作（L10 他人主页）─────────────────────────────────────────────
    //
    // 三端点用 retrofit2.Response 承接状态码而非直接返回 Unit：GitHub 把
    // 「是否已关注」编码在状态码里（**204 = 已关注 / 404 = 未关注**），
    // 直接返回 Unit 会让 404 变成 HttpException，把正常语义当异常吞掉。

    /** GET /user/following/{username}：当前用户是否关注了该用户（204 = 已关注，404 = 未关注） */
    @GET("user/following/{username}")
    suspend fun isFollowing(
        @Path("username") username: String,
    ): Response<Unit>

    /** PUT /user/following/{username}：关注（204 = 成功） */
    @PUT("user/following/{username}")
    suspend fun follow(
        @Path("username") username: String,
    ): Response<Unit>

    /** DELETE /user/following/{username}：取关（204 = 成功） */
    @DELETE("user/following/{username}")
    suspend fun unfollow(
        @Path("username") username: String,
    ): Response<Unit>
}
