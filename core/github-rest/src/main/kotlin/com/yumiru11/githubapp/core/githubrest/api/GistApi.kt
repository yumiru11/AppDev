package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.GistDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gist REST 接口（L11 / ui-design.md §3.7）。
 *
 * v1 只做列表（条目点击经 Chrome Custom Tabs 打开 html_url），不做 gist 详情渲染。
 * 走 `users/{username}/gists` 公开端点：本人主页与他人主页共用一条路径
 * （/gists 认证端点会带出 secret gist，v1 明面上只展示公开数据）。
 */
interface GistApi {
    /** GET /users/{username}/gists：用户公开 Gist 列表（page/per_page 分页） */
    @GET("users/{username}/gists")
    suspend fun userGists(
        @Path("username") username: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): List<GistDto>
}
