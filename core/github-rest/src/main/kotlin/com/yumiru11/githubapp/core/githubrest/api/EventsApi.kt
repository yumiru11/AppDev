package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.EventDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 用户事件 REST 接口（T10 首页动态流数据源）。
 *
 * GET /users/{login}/received_events：用户收到的事件（star/issue/PR/push 等），
 * page/per_page 整数分页。Retrofit 3 原生 suspend：非 2xx 自动抛 [retrofit2.HttpException]。
 */
interface EventsApi {
    /** GET /users/{login}/received_events：用户收到的事件列表 */
    @GET("users/{login}/received_events")
    suspend fun receivedEvents(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<EventDto>
}
