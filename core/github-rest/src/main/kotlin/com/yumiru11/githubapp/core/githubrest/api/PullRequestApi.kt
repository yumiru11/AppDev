package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CheckRunsResponseDto
import com.yumiru11.githubapp.core.githubrest.model.CombinedStatusDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestCommitDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestFileDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub REST Pull Request 接口（T15 PR 列表与详情）。
 *
 * 端点：
 * - GET /repos/{owner}/{repo}/pulls — 列表（state 过滤 + 页码分页）
 * - GET /repos/{owner}/{repo}/pulls/{number} — 详情（含 mergeable/head/base）
 * - GET /repos/{owner}/{repo}/pulls/{number}/commits — 提交列表（含文件变更摘要）
 * - GET /repos/{owner}/{repo}/pulls/{number}/files — 文件变更列表（+N −M + patch）
 * - GET /repos/{owner}/{repo}/issues/{number}/timeline — 时间线（评论/Review/事件，PR 复用 Issue 端点）
 * - GET /repos/{owner}/{repo}/commits/{ref}/check-runs — Check Run 列表
 * - GET /repos/{owner}/{repo}/commits/{ref}/status — 合并状态摘要
 *
 * 与 [IssueApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 * 时间线端点需 mockingbird preview Accept（GitHub 时间线媒体类型）。
 */
interface PullRequestApi {
    /**
     * GET /repos/{owner}/{repo}/pulls：PR 列表（分页，state 过滤）。
     *
     * @param state "open" / "closed" / "all"
     * @param page 页码（1 起）
     * @param perPage 每页条数
     */
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun listPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<PullRequestDto>

    /**
     * GET /repos/{owner}/{repo}/pulls/{number}：单个 PR 详情。
     */
    @GET("repos/{owner}/{repo}/pulls/{number}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): PullRequestDto

    /**
     * GET /repos/{owner}/{repo}/pulls/{number}/commits：PR 提交列表。
     *
     * 每项含 commit 元数据（message/author date）与 files 变更摘要（+N −M）。
     */
    @GET("repos/{owner}/{repo}/pulls/{number}/commits")
    suspend fun listCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 100,
    ): List<PullRequestCommitDto>

    /**
     * GET /repos/{owner}/{repo}/pulls/{number}/files：PR 文件变更列表。
     *
     * 每项含 filename/status/additions/deletions/changes/patch。
     */
    @GET("repos/{owner}/{repo}/pulls/{number}/files")
    suspend fun listFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 100,
    ): List<PullRequestFileDto>

    /**
     * GET /repos/{owner}/{repo}/issues/{number}/timeline：PR 时间线。
     *
     * 返回混合数组（评论/Review/行内评论/提交引用/事件），每项含 `event` 判别字段。
     * PR 与 Issue 共用该端点（GitHub 行为），需 mockingbird preview Accept。
     */
    @GET("repos/{owner}/{repo}/issues/{number}/timeline")
    suspend fun listTimeline(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Header("Accept") accept: String = ACCEPT_TIMELINE,
    ): List<IssueEventDto>

    /**
     * GET /repos/{owner}/{repo}/commits/{ref}/check-runs：Check Run 列表。
     *
     * @param ref 提交 SHA（PR head sha）
     */
    @GET("repos/{owner}/{repo}/commits/{ref}/check-runs")
    suspend fun listCheckRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): CheckRunsResponseDto

    /**
     * GET /repos/{owner}/{repo}/commits/{ref}/status：合并状态摘要。
     *
     * state = success / failure / pending（Checks 摘要行用）。
     *
     * @param ref 提交 SHA（PR head sha）
     */
    @GET("repos/{owner}/{repo}/commits/{ref}/status")
    suspend fun getCombinedStatus(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): CombinedStatusDto

    private companion object {
        /** 时间线及 Reviews/Events 扩展媒体类型（GitHub preview） */
        const val ACCEPT_TIMELINE = "application/vnd.github.mockingbird-preview+json"
    }
}
