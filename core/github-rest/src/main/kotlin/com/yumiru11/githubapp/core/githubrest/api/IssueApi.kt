package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CreateCommentRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateIssueRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateReactionRequest
import com.yumiru11.githubapp.core.githubrest.model.IssueCommentDto
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.core.githubrest.model.ReactionDto
import com.yumiru11.githubapp.core.githubrest.model.UpdateIssueRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub REST Issue 接口（T13 列表/详情 + T14 写操作）。
 *
 * 读端点：
 * - GET /repos/{owner}/{repo}/issues — 列表（state 过滤 + Link 头分页）
 * - GET /repos/{owner}/{repo}/issues/{number} — 详情
 * - GET /repos/{owner}/{repo}/issues/{number}/timeline — 时间线（评论/事件/交叉引用/关联 PR）
 *
 * 写端点（T14）：
 * - POST /repos/{owner}/{repo}/issues — 创建 Issue
 * - PATCH /repos/{owner}/{repo}/issues/{number} — 编辑 title/body/state/labels/assignees/milestone
 * - POST/PATCH/DELETE .../issues/{number}/comments 与 .../issues/comments/{id} — 评论增改删
 * - POST/DELETE .../reactions — 反应增删（需 squirrel-girl preview Accept）
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

    /**
     * POST /repos/{owner}/{repo}/issues：创建 Issue（T14）。
     *
     * @param title 标题（必填）
     * @param body 正文（可空）
     * @param labels 标签名列表（可空）
     */
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest,
    ): IssueDto

    /**
     * PATCH /repos/{owner}/{repo}/issues/{number}：更新 Issue（T14）。
     *
     * 请求体仅携带非空字段（[UpdateIssueRequest] 自定义序列化器），
     * 避免 GitHub 对 labels/assignees/milestone 的 null 清空语义误伤未变更字段。
     */
    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body request: UpdateIssueRequest,
    ): IssueDto

    /**
     * POST /repos/{owner}/{repo}/issues/{number}/comments：新增评论（T14）。
     */
    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun createComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body request: CreateCommentRequest,
    ): IssueCommentDto

    /**
     * PATCH /repos/{owner}/{repo}/issues/comments/{comment_id}：编辑评论（T14）。
     */
    @PATCH("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun updateComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body request: CreateCommentRequest,
    ): IssueCommentDto

    /**
     * DELETE /repos/{owner}/{repo}/issues/comments/{comment_id}：删除评论（T14）。
     * GitHub 返回 204 空体。
     */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun deleteComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
    ): Response<Unit>

    /**
     * POST /repos/{owner}/{repo}/issues/{number}/reactions：给 Issue 加反应（T14）。
     * 需 squirrel-girl preview Accept。
     */
    @POST("repos/{owner}/{repo}/issues/{number}/reactions")
    suspend fun addIssueReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body request: CreateReactionRequest,
        @Header("Accept") accept: String = ACCEPT_REACTIONS,
    ): ReactionDto

    /**
     * DELETE /repos/{owner}/{repo}/issues/{number}/reactions/{reaction_id}：删除 Issue 反应（T14）。
     * 仅能删除本人添加的反应；需 squirrel-girl preview Accept。
     */
    @DELETE("repos/{owner}/{repo}/issues/{number}/reactions/{reaction_id}")
    suspend fun removeIssueReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Path("reaction_id") reactionId: Long,
        @Header("Accept") accept: String = ACCEPT_REACTIONS,
    ): Response<Unit>

    /**
     * POST /repos/{owner}/{repo}/issues/comments/{comment_id}/reactions：给评论加反应（T14）。
     */
    @POST("repos/{owner}/{repo}/issues/comments/{comment_id}/reactions")
    suspend fun addCommentReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body request: CreateReactionRequest,
        @Header("Accept") accept: String = ACCEPT_REACTIONS,
    ): ReactionDto

    /**
     * DELETE /repos/{owner}/{repo}/issues/comments/{comment_id}/reactions/{reaction_id}：删除评论反应（T14）。
     */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}/reactions/{reaction_id}")
    suspend fun removeCommentReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Path("reaction_id") reactionId: Long,
        @Header("Accept") accept: String = ACCEPT_REACTIONS,
    ): Response<Unit>

    private companion object {
        /** 时间线及 Reviews/Events 扩展媒体类型（GitHub preview） */
        const val ACCEPT_TIMELINE = "application/vnd.github.mockingbird-preview+json"

        /** 反应端点扩展媒体类型（GitHub preview） */
        const val ACCEPT_REACTIONS = "application/vnd.github.squirrel-girl-preview+json"
    }
}
