package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.AuthTokenInterceptor
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagCacheInterceptor
import com.yumiru11.githubapp.core.githubrest.http.EtagStore
import com.yumiru11.githubapp.core.githubrest.http.GitHubHeaderInterceptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * GitHub REST 客户端工厂：共享 OkHttp（Auth/统一头/ETag/日志拦截器链）+ Retrofit 3。
 *
 * 工厂保持无状态可测（测试直接替换 baseUrl 指向 MockWebServer）；
 * Hilt 装配见 di/RestNetworkModule。
 */
object GitHubRestClient {
    private val JSON_CONTENT_TYPE = "application/json".toMediaType()

    /** snake_case ↔ camelCase 自动映射；容忍 GitHub 新增字段 */
    fun createJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }

    /**
     * 共享 OkHttpClient，拦截器顺序（外→内）：
     * ETag（条件请求/304 回放）→ 统一头 → 认证头 → 日志（仅 debug）。
     */
    fun createOkHttpClient(
        tokenProvider: TokenProvider,
        etagStore: EtagStore,
        debugLogging: Boolean,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(EtagCacheInterceptor(etagStore))
            .addInterceptor(GitHubHeaderInterceptor())
            .addInterceptor(AuthTokenInterceptor(tokenProvider))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (debugLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                },
            ).connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun createRetrofit(
        baseUrl: HttpUrl,
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_CONTENT_TYPE))
            .build()

    private const val DEFAULT_TIMEOUT_SECONDS = 30L
}
