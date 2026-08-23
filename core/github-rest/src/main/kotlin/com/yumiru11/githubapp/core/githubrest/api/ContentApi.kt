package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.ContentWriteResponseDto
import com.yumiru11.githubapp.core.githubrest.model.FileContentDto
import com.yumiru11.githubapp.core.githubrest.model.FileDeleteRequest
import com.yumiru11.githubapp.core.githubrest.model.FileWriteRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contents API 接口（T11 代码浏览 + T22 文件编辑提交，plan.md §4.5/§7.4）。
 *
 * 端点：
 * - GET    /repos/{owner}/{repo}/contents/{path} — 读取（T11）
 * - PUT    /repos/{owner}/{repo}/contents/{path} — 创建/更新（T22）
 * - DELETE /repos/{owner}/{repo}/contents/{path} — 删除（T22；参数经 JSON body，官方 curl 示例同款）
 *
 * 与 [ReadmeApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 * 409（sha 过期冲突）响应体 message 内嵌当前文件 sha（"<path> does not match <sha>"，
 * 2026-08-22 真实验证），由上层解析驱动冲突对话框（绝不静默覆盖）。
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

    /**
     * 创建/更新文件（T22，plan.md §7.4）。
     *
     * @param request [FileWriteRequest]：message/content 必填；sha = null 新建、非 null 更新；
     *   branch = null 默认分支；分支不存在时 GitHub 自动创建（2026-08-22 实测），
     *   故「提交到新分支」无需先建 ref
     */
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: FileWriteRequest,
    ): ContentWriteResponseDto

    /**
     * 删除文件（T22）。
     *
     * GitHub 对 DELETE 接受 JSON body（官方 curl 示例 -d 同款，2026-08-22 实测）；
     * Retrofit 的 @DELETE 不允许 @Body，故用 [@HTTP](http.HTTP) hasBody 显式声明。
     *
     * @param request [FileDeleteRequest]：message/sha 必填；branch = null 默认分支
     */
    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: FileDeleteRequest,
    ): ContentWriteResponseDto
}
