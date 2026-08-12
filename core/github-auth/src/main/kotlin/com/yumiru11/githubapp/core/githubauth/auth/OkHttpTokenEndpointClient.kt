// 统一包装 token 交换的所有失败为 TokenExchangeException（TokenExchangeException 先行重抛保持类型）；
// 泛化 catch 是包装层收口设计，非缺陷。
@file:Suppress("TooGenericExceptionCaught")

package com.yumiru11.githubapp.core.githubauth.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * [TokenEndpointClient] 的 OkHttp 生产实现。
 *
 * 与 [com.yumiru11.githubapp.core.githubauth.session.TokenRefresher] 同构：
 * 裸 OkHttpClient（无认证拦截器——token 端点请求不应注入凭据），
 * `Accept: application/json` 要求 GitHub 返回 JSON，表单提交授权码。
 */
class OkHttpTokenEndpointClient
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val config: OAuthConfig,
    ) : TokenEndpointClient {
        override suspend fun exchangeCode(code: String): TokenExchangeResult =
            withContext(Dispatchers.IO) {
                try {
                    client.newCall(buildTokenRequest(code)).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw TokenExchangeException("token endpoint returned HTTP ${response.code}")
                        }
                        val parsed = TOKEN_JSON.decodeFromString<TokenExchangeResponse>(response.body.string())
                        val access = parsed.accessToken
                        if (access.isNullOrBlank()) {
                            throw TokenExchangeException("token endpoint returned no access_token")
                        }
                        TokenExchangeResult(accessToken = access, refreshToken = parsed.refreshToken)
                    }
                } catch (e: TokenExchangeException) {
                    throw e
                } catch (e: Exception) {
                    // 网络/解析错误统一包装（token 内容绝不进异常消息/日志）；cause 保留原始异常链
                    throw TokenExchangeException("token exchange failed: ${e.javaClass.simpleName}", e)
                }
            }

        private fun buildTokenRequest(code: String): Request =
            Request
                .Builder()
                .url(config.tokenEndpoint)
                .header(HEADER_ACCEPT, MEDIA_TYPE_JSON)
                .post(
                    FormBody
                        .Builder()
                        .add(PARAM_GRANT_TYPE, GRANT_TYPE_AUTHORIZATION_CODE)
                        .add(PARAM_CODE, code)
                        .add(PARAM_CLIENT_ID, config.clientId)
                        .add(PARAM_REDIRECT_URI, config.redirectUri)
                        .build(),
                ).build()

        private companion object {
            const val HEADER_ACCEPT = "Accept"
            const val MEDIA_TYPE_JSON = "application/json"
            const val PARAM_GRANT_TYPE = "grant_type"
            const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
            const val PARAM_CODE = "code"
            const val PARAM_CLIENT_ID = "client_id"
            const val PARAM_REDIRECT_URI = "redirect_uri"

            @OptIn(ExperimentalSerializationApi::class)
            val TOKEN_JSON: Json =
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                }
        }

        @Serializable
        private data class TokenExchangeResponse(
            val accessToken: String? = null,
            val refreshToken: String? = null,
        )
    }
