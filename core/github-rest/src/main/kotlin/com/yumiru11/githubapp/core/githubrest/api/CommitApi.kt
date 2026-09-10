package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CommitDetailDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Commit REST 接口（L09：COMMIT 详情页）。
 *
 * 与其余 REST 接口一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]
 * （404 仓库/提交不存在、403 无权限、451 因 DMCA 下架）。
 *
 * 分页：单提交端点对 `files` 数组分页（`per_page` 上限 100，单提交最多 300 个文件），
 * 由仓库层按页累积——[perPage] 与 [page] 需显式传入。
 */
interface CommitApi {
    /**
     * GET /repos/{owner}/{repo}/commits/{ref}：单提交详情（含文件变更与 patch）。
     *
     * @param ref commit SHA / 分支名 / Tag 名
     * @param perPage 每页文件数（上限 100）
     * @param page 页码（从 1 起）
     */
    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): CommitDetailDto
}
