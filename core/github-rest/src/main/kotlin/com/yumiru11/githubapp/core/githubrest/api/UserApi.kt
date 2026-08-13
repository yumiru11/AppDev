package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import retrofit2.http.GET
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
}
