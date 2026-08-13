package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.MarkdownRenderRequest
import com.yumiru11.githubapp.core.githubrest.model.ReadmeDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * README 与 Markdown 服务端渲染接口（T9 README 浏览 tracer bullet）。
 *
 * 端点：
 * - GET /repos/{owner}/{repo}/readme — README 元数据 + base64 内容（JSON）或服务端渲染 HTML（html+json Accept）
 * - POST /markdown — 服务端 GFM 渲染（备用：原生路径 + 服务端 HTML 双轨）
 *
 * 与 [RepositoryApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 */
interface ReadmeApi {
    /**
     * GET /repos/{owner}/{repo}/readme，Accept: application/vnd.github.html+json
     *
     * 返回服务端已渲染 HTML（GFM 全特性：表格、任务列表、mermaid 等）。
     * 走 WebView 兜底通道时使用此 HTML（已相对仓库解析链接）。
     *
     * @param owner 仓库所有者
     * @param repo 仓库名
     * @param ref 可选分支/Tag/SHA（默认分支为 null）
     */
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadmeHtml(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = ACCEPT_HTML,
        @Query("ref") ref: String? = null,
    ): ResponseBody

    /**
     * GET /repos/{owner}/{repo}/readme，Accept: application/json
     *
     * 返回 README 元数据 + base64 编码内容（含 sha 用于 ETag 校验、download_url 用于相对路径基准）。
     * 原生渲染路径用此取回 Markdown 文本。
     */
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadmeMeta(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = ACCEPT_JSON,
        @Query("ref") ref: String? = null,
    ): ReadmeDto

    /**
     * POST /markdown — 服务端 GFM 渲染（备用：无 README 但需渲染任意 Markdown 时使用）。
     *
     * @param body 渲染请求体（text + mode + context）
     * @return 渲染后 HTML 字符串（ResponseBody）
     */
    @POST("markdown")
    suspend fun renderMarkdown(
        @Body body: MarkdownRenderRequest,
    ): ResponseBody

    private companion object {
        const val ACCEPT_HTML = "application/vnd.github.html+json"
        const val ACCEPT_JSON = "application/json"
    }
}
