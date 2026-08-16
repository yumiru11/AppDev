package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.GitTreeResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Git Data Tree 接口（T11 文件树，plan.md §3.2「仓库树、分支、提交 → Git Data API」）。
 *
 * 端点：GET /repos/{owner}/{repo}/git/trees/{treeSha}
 *
 * 按需展开策略：请求不带 `recursive` 参数（默认非递归），根树返回根级条目，
 * 目录条目点击后再以其 SHA 请求子树——避免大仓库递归树 truncated/超长响应。
 *
 * 与 [RepositoryApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 */
interface GitTreeApi {
    /**
     * 获取树（非递归）。
     *
     * @param owner 仓库所有者
     * @param repo 仓库名
     * @param treeSha 树 SHA（根树 = 分支 head commit 的 tree SHA；子树 = 目录条目 SHA）
     * @return 树响应（条目 path 相对所请求树）
     */
    @GET("repos/{owner}/{repo}/git/trees/{treeSha}")
    suspend fun getTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("treeSha") treeSha: String,
    ): GitTreeResponseDto
}
