package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.NotificationDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 通知 REST 接口（写优先通道，Retrofit 3 原生 suspend）。
 *
 * - GET /notifications：通知列表（[all] 含已读、[participating] 仅参与、page 分页）
 * - PATCH /notifications/threads/{thread_id}：单条已读（GitHub 返回 205 Reset Content 空体）
 * - PATCH /notifications：全部已读
 * - DELETE /notifications/threads/{thread_id}：标记单条 done（#88 面板左滑删除，204 空响应）
 *
 * mention 无独立服务端参数（GitHub API 仅 all/participating），由调用方按 reason 客户端过滤。
 */
interface NotificationApi {
    /** GET /notifications：通知列表（all/participating 传 null 时不携带该查询参数） */
    @GET("notifications")
    suspend fun listNotifications(
        @Query("all") all: Boolean? = null,
        @Query("participating") participating: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<NotificationDto>

    /** PATCH /notifications/threads/{thread_id}：标记单条已读 */
    @PATCH("notifications/threads/{thread_id}")
    suspend fun markThreadRead(
        @Path("thread_id") threadId: String,
    ): Response<Unit>

    /** PATCH /notifications：标记全部已读（空体请求，服务端 205 空响应） */
    @PATCH("notifications")
    suspend fun markAllRead(): Response<Unit>

    /** DELETE /notifications/threads/{thread_id}：标记单条 done（面板左滑删除，服务端 204 空响应） */
    @DELETE("notifications/threads/{thread_id}")
    suspend fun markThreadDone(
        @Path("thread_id") threadId: String,
    ): Response<Unit>
}
