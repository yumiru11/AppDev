package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub REST Issue 接口（T13 Issue 列表与详情）。
 *
 * 端点：
 * - GET /repos/{owner}/{repo}/issues — 列表（state 过滤 + Link 头分页）
 * - GET /repos/{owner}/{repo}/issues/{number} — 详情
 * - GET /repos/{owner}/{repo}/issues/{number}/timeline — 时间线（评论/事件/交叉引用/关联 PR）
 *
 * 与 [RepositoryApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 * 时间线端点需 mockingbird preview Accept（GitHub 时间线媒体类型）。
 */
interface IssueApi {
    /**
     * GET /repos/{owner}/{repo}/issues：Issue 列表（分页，state 过滤）。
     *
     * @param state "open" / "closed" / "all"
     * @param page 页码（1 起）
     * @param perPage 每页条数
     */
    @GET("repos/{owner}/{repo}/issues")
    suspend fun listIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<IssueDto>

    /**
     * GET /repos/{owner}/{repo}/issues/{number}：单个 Issue 详情。
     */
    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): IssueDto

    /**
     * GET /repos/{owner}/{repo}/issues/{number}/timeline：Issue 时间线。
     *
     * 返回混合数组（评论/事件/交叉引用/关联 PR），每项含 `event` 判别字段。
     * 需 mockingbird preview Accept。
     */
    @GET("repos/{owner}/{repo}/issues/{number}/timeline")
    suspend fun listTimeline(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Header("Accept") accept: String = ACCEPT_TIMELINE,
    ): List<IssueEventDto>

    private companion object {
        /** 时间线及 Reviews/Events 扩展媒体类型（GitHub preview） */
        const val ACCEPT_TIMELINE = "application/vnd.github.mockingbird-preview+json"
    }
}
