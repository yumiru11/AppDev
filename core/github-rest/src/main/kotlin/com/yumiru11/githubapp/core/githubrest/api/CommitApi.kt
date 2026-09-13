package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CommitDetailDto
import com.yumiru11.githubapp.core.githubrest.model.CommitListItemDto
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

    /**
     * GET /repos/{owner}/{repo}/commits?sha=&path=：提交列表（可按路径过滤）。
     *
     * 文件树「修改时间」列的数据源（UI-6）：GitHub **公开 API 不提供 per-file mtime**
     * （tree/contents 响应都只有 sha/size/mode），唯一权威来源就是「该路径最近一次提交」——
     * `?path=` 过滤在文件上是精确匹配、在目录上匹配其下全部内容（GitHub 网页文件列表同源语义）。
     *
     * 调用方传 `perPage = 1` 即得该路径的最后一次提交（列表按时间倒序）。
     *
     * @param owner 仓库所有者
     * @param repo 仓库名
     * @param sha 分支/Tag/SHA（列表起点）
     * @param path 仓库内路径（文件或目录）
     * @param perPage 每页条数（列表「修改时间」列用 1）
     */
    @GET("repos/{owner}/{repo}/commits")
    suspend fun listCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") sha: String,
        @Query("path") path: String,
        @Query("per_page") perPage: Int,
    ): List<CommitListItemDto>
}
