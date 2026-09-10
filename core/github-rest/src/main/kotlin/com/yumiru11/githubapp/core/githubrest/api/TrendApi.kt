package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.TrendDto
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Trending 镜像 REST 接口（ui-design.md §2.5）。
 *
 * 镜像站与 api.github.com **不同源**，故走 [Url] 全量地址（Retrofit 会忽略 baseUrl）；
 * 请求由专用 OkHttpClient 承载——镜像站是第三方静态托管，**绝不能带 GitHub token**
 * （共享客户端的 AuthTokenInterceptor 对任意 host 无差别注入 Authorization）。
 */
interface TrendApi {
    /** GET {mirrorUrl}：榜单 JSON 数组（每日/每周/每月由调用方拼地址） */
    @GET
    suspend fun trending(
        @Url url: String,
    ): List<TrendDto>
}
