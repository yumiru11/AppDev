package com.yumiru11.githubapp.core.githubauth.auth

/**
 * Token 端点网络交换抽象（可注入 seam）。
 *
 * 把「授权码 → access/refresh token」的 HTTP 层从 [OAuthSessionManager] 中抽离：
 * 生产实现 [OkHttpTokenEndpointClient] 走 OkHttp；测试注入 MockWebServer 指向的实现，
 * 使 HTTP 层可在纯 JVM 下验证（AppAuth 的 [net.openid.appauth.AuthorizationService]
 * 依赖 Android 上下文，其 performTokenRequest 路径标注「需真机验证」）。
 */
interface TokenEndpointClient {
    /**
     * 用授权码向 token 端点换 token。
     *
     * @throws TokenExchangeException 端点非 2xx、响应缺 access_token、网络错误。
     */
    suspend fun exchangeCode(code: String): TokenExchangeResult
}

/**
 * Token 端点换 token 结果（GitHub 公开客户端返回，无 client_secret）。
 *
 * refreshToken 为 null 表示 GitHub 未返回（正常情况 GitHub 授权码流程会返回，
 * 容忍缺失并持久化 null，与 TokenRefresher 的轮换语义一致）。
 */
data class TokenExchangeResult(
    val accessToken: String,
    val refreshToken: String?,
)

/** 换 token 失败（授权码无效/过期、端点异常响应、网络错误）。 */
class TokenExchangeException(
    message: String,
) : Exception(message)
