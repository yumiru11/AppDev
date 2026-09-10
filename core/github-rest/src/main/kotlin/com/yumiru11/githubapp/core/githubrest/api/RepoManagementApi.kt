package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.CreateReleaseRequest
import com.yumiru11.githubapp.core.githubrest.model.ReleaseAssetDto
import com.yumiru11.githubapp.core.githubrest.model.ReleaseDto
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionRequest
import com.yumiru11.githubapp.core.githubrest.model.TagDto
import com.yumiru11.githubapp.core.githubrest.model.TopicsDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 仓库管理 REST 接口（T12：Star/Watch/Fork + Releases/Tags/Languages）。
 *
 * 与 [RepositoryApi] 一致：Retrofit 3 原生 suspend，非 2xx 自动抛 [retrofit2.HttpException]。
 * 状态检查端点语义：
 * - GET /user/starred/{o}/{r}：204 已星标 / 404 未星标（401/403 游客 → 调用方按 false 处理）
 * - GET /repos/{o}/{r}/subscription：200 {subscribed,...} / 404 未订阅
 * - POST /repos/{o}/{r}/forks：202 返回新仓库；403 无权限；422 已 Fork 过
 */
interface RepoManagementApi {
    /** GET /user/starred/{owner}/{repo}：是否已星标（204 是 / 404 否） */
    @GET("user/starred/{owner}/{repo}")
    suspend fun isStarred(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** PUT /user/starred/{owner}/{repo}：星标（204） */
    @PUT("user/starred/{owner}/{repo}")
    suspend fun star(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** DELETE /user/starred/{owner}/{repo}：取消星标（204） */
    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstar(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** GET /repos/{owner}/{repo}/subscription：Watch 状态（200 {subscribed} / 404 未订阅） */
    @GET("repos/{owner}/{repo}/subscription")
    suspend fun getSubscription(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): SubscriptionDto

    /** PUT /repos/{owner}/{repo}/subscription：Watch（body {"subscribed":true}） */
    @PUT("repos/{owner}/{repo}/subscription")
    suspend fun watch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: SubscriptionRequest,
    ): SubscriptionDto

    /** DELETE /repos/{owner}/{repo}/subscription：Unwatch（204） */
    @DELETE("repos/{owner}/{repo}/subscription")
    suspend fun unwatch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** POST /repos/{owner}/{repo}/forks：Fork（202 返回新仓库；403 无权限；422 已 Fork） */
    @POST("repos/{owner}/{repo}/forks")
    suspend fun fork(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): RepositoryDto

    /** GET /repos/{owner}/{repo}/releases：Release 列表 */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<ReleaseDto>

    /** GET /repos/{owner}/{repo}/releases/{id}：Release 详情 */
    @GET("repos/{owner}/{repo}/releases/{releaseId}")
    suspend fun getRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("releaseId") releaseId: Long,
    ): ReleaseDto

    /** GET /repos/{owner}/{repo}/tags：Tag 列表 */
    @GET("repos/{owner}/{repo}/tags")
    suspend fun listTags(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<TagDto>

    /** GET /repos/{owner}/{repo}/languages：语言 → 字节数（Linguist 数据） */
    @GET("repos/{owner}/{repo}/languages")
    suspend fun getLanguages(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Map<String, Long>

    /**
     * POST /repos/{owner}/{repo}/releases：创建 Release（L05）。
     *
     * 403 无写权限 / 422 校验失败（如 tag 非法、已存在）自动抛 [retrofit2.HttpException]。
     */
    @POST("repos/{owner}/{repo}/releases")
    suspend fun createRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateReleaseRequest,
    ): ReleaseDto

    /**
     * POST /repos/{owner}/{repo}/releases/{releaseId}/assets?name={name}：上传 Release 附件（L05）。
     *
     * 官方示例用 query 参数传 `name`（非表单字段），文件本体走 multipart 的 `file` part；
     * 403 无权限 / 422 校验失败（重名、附件过大）抛 [retrofit2.HttpException]。
     */
    @Multipart
    @POST("repos/{owner}/{repo}/releases/{releaseId}/assets")
    suspend fun uploadReleaseAsset(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("releaseId") releaseId: Long,
        @Query("name") name: String,
        @Part file: MultipartBody.Part,
    ): ReleaseAssetDto

    /**
     * GET /repos/{owner}/{repo}/topics：仓库 Topics（L06）。
     *
     * 响应为 `{"names": [...]}` 包装对象（与多数 REST 端点直接返回数组不同）。
     */
    @GET("repos/{owner}/{repo}/topics")
    suspend fun getTopics(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): TopicsDto

    /**
     * DELETE /repos/{owner}/{repo}/git/refs/heads/{branch}：删除分支（T17 MergeBox，204）。
     *
     * 默认分支不可删（GitHub 返回 422）；仅 WRITE 权限可用。
     */
    @DELETE("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun deleteBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
    ): Response<Unit>
}
