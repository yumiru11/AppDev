package com.yumiru11.githubapp.core.githubauth.session

import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证会话拦截器：注入凭据 + 401 静默刷新 + 单次重放（认证保持层）。
 *
 * 与 T5 已合入 core:github-rest 的 [com.yumiru11.githubapp.core.githubrest.auth.AuthTokenInterceptor]
 * 协调决策：**新建不增强**——本拦截器是超集（注入 + 刷新 + 重放），且 core:github-auth 不应
 * 反向依赖 core:github-rest 的 TokenProvider seam；T5 拦截器保持原样（游客期/简单注入用，
 * 已合入勿动）。两者同链共存时均用 `header()` 覆盖语义，写入同源值，无冲突。
 *
 * ## 凭据选择（accessToken 与 pat 互斥，SessionData 语义）
 * - OAuth 模式：注入 `Bearer {accessToken}`
 * - PAT 模式（isRestOnly，ADR-0003）：注入 `Bearer {pat}`（REST 接受 PAT 作 bearer）
 * - 游客：不注入
 *
 * ## 401 处理
 * 收到 401 且请求携带凭据 → [TokenRefresher.refreshIfNeeded] 刷新（并发防护在刷新器内）→
 * 用新 token 重放原请求；刷新失败/无刷新能力（PAT）→ 原样返回 401 由上层归一化。
 * 重放请求打 [RetryMarker] 标记，保证**最多重放一次**，防止 401 死循环。
 */
class AuthSessionInterceptor(
    private val tokenStorage: TokenStorage,
    private val refresher: TokenRefresher,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Token 端点请求不注入认证头、不参与刷新（refresher 应使用裸客户端；此处为防御性兜底）
        if (request.url == refresher.tokenEndpoint) {
            return chain.proceed(request)
        }

        // 已重放过的请求直接放行：最多重放一次
        if (request.tag(RetryMarker::class) != null) {
            return chain.proceed(request)
        }

        val token = tokenStorage.loadSession().let { it.accessToken ?: it.pat }
        val authenticated =
            if (token == null) {
                request
            } else {
                request.newBuilder().header(HEADER_AUTHORIZATION, "Bearer $token").build()
            }

        val response = chain.proceed(authenticated)
        if (response.code != HTTP_UNAUTHORIZED || token == null) return response

        // 401：刷新一次；成功则丢弃原始 401 响应并重放
        val fresh =
            runBlocking { refresher.refreshIfNeeded() }
                .getOrNull()
                ?.takeIf { it }
                ?.let { tokenStorage.loadSession().accessToken }
        if (fresh == null) return response

        response.close()
        val retry =
            response
                .request
                .newBuilder()
                .tag(RetryMarker::class, RetryMarker)
                .header(HEADER_AUTHORIZATION, "Bearer $fresh")
                .build()
        return chain.proceed(retry)
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HTTP_UNAUTHORIZED = 401

        /** 重放标记（KClass 键，不覆盖调用方可能设置的 tag） */
        object RetryMarker
    }
}
