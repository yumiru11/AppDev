package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.GitRefCreateRequest
import com.yumiru11.githubapp.core.githubrest.model.GitRefDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
        @Body body: GitRefCreateRequest,
    ): GitRefDto
}
