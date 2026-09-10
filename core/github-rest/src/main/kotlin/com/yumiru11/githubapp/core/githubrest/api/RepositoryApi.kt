package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 仓库 REST 接口（写优先通道基础端点）。
 */
interface RepositoryApi {
    /** GET /repos/{owner}/{repo}：仓库元数据（含当前会话 permissions） */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): RepositoryDto

    /**
     * DELETE /repos/{owner}/{repo}：删除仓库（L04，204）。
     *
     * 仅 owner / admin 权限可用；无权限（403）与不存在（404）抛 [retrofit2.HttpException]。
     * 用 [Response] 承接状态码：204 无响应体，直接返回 Unit 会与错误响应混淆。
     */
    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>
}
