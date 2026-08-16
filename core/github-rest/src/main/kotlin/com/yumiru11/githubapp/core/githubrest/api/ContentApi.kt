package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.FileContentDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contents API 接口（T11 代码浏览，plan.md §4.5「文件内容 → Contents API」）。
 *
 * 端点：GET /repos/{owner}/{repo}/contents/{path}
 *
 * 与 [ReadmeApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 */
interface ContentApi {
    /**
     * 获取单文件内容（base64 编码）。
     *
     * @param owner 仓库所有者
     * @param repo 仓库名
     * @param path 文件路径（可含子目录，如 "src/main/Main.kt"；Retrofit @Path 保留 "/"）
     * @param ref 分支/Tag/SHA（null = 默认分支）
     */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null,
    ): FileContentDto
}
