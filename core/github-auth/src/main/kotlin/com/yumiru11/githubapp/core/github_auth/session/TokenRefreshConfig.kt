package com.yumiru11.githubapp.core.github_auth.session

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

/**
 * Token 刷新端点配置。
 *
 * GitHub OAuth refresh_token 流程（plan.md §4.1）：`POST /login/oauth/access_token`
 * 携带 `grant_type=refresh_token` + 旧 refresh token + `client_id`（公开客户端，无 client_secret）。
 * 端点与 clientId 由装配方注入（测试指向 MockWebServer，生产指向 GitHub）。
 */
data class TokenRefreshConfig(
    val tokenEndpoint: HttpUrl,
    val clientId: String,
)

/**
 * Token 端点响应（GitHub 返回 snake_case JSON）。
 *
 * GitHub 每次刷新都会轮换 refresh token：刷新成功后必须用新值覆盖存储。
 * 字段缺失（GitHub 新增字段/部分响应）一律容忍。
 */
@Serializable
internal data class TokenEndpointResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

/**
 * 刷新失败（refresh token 过期/吊销、网络错误、端点异常响应）。
 * 上层据此类决定「登出信号」：refresh token 失效即静默会话已不可续期。
 */
class TokenRefreshException(message: String) : Exception(message)
