package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.UserDto
import retrofit2.http.GET

/**
 * 认证用户 REST 接口（写优先通道基础端点）。
 *
 * Retrofit 3 原生 suspend：非 2xx 自动抛 [retrofit2.HttpException]。
 */
interface UserApi {
    /** GET /user：当前认证用户资料 */
    @GET("user")
    suspend fun currentUser(): UserDto
}
