package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.BranchDto
import com.yumiru11.githubapp.core.githubrest.model.GitRefCreateRequest
import com.yumiru11.githubapp.core.githubrest.model.GitRefDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Git References API（T22 新建分支前置 / T23 分支管理复用）。
 *
 * - GET  /repos/{owner}/{repo}/git/ref/heads/{ref} — 查询单个分支（取 base SHA）
 * - POST /repos/{owner}/{repo}/git/refs            — 创建引用
 */
interface GitRefApi {
    @GET("repos/{owner}/{repo}/git/ref/heads/{ref}")
    suspend fun getBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): GitRefDto

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GitRefCreateRequest,
    ): GitRefDto

    /**
     * GET /repos/{owner}/{repo}/branches：分支列表（T23 分支管理）。
     *
     * @param perPage 每页条数（GitHub 默认 30；分支多的仓库用 100）
     */
    @GET("repos/{owner}/{repo}/branches")
    suspend fun listBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<BranchDto>
}
