package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 仓库 REST 接口（写优先通道基础端点）。
 */
interface RepositoryApi {
    /** GET /repos/{owner}/{repo}：仓库元数据 */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): RepositoryDto
}
