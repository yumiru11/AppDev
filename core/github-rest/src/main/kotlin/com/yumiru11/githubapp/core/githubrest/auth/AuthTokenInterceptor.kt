package com.yumiru11.githubapp.core.githubrest.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证头注入拦截器：有令牌时注入 `Authorization: Bearer {token}`（plan.md §4.3）。
 *
 * 无令牌（游客）时不注入，GitHub 对公共内容允许匿名访问（限流更低）。
 * 使用 [Interceptor.header] 语义：覆盖调用方可能携带的过期 Authorization。
 */
class AuthTokenInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.token() ?: return chain.proceed(chain.request())
        val authenticated =
            chain
                .request()
                .newBuilder()
                .header(HEADER_AUTHORIZATION, "Bearer $token")
                .build()
        return chain.proceed(authenticated)
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
