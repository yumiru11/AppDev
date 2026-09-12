package com.yumiru11.githubapp.core.githubrest.api

import android.util.Log
import com.yumiru11.githubapp.core.common.logging.LogRedaction
import com.yumiru11.githubapp.core.githubrest.auth.AuthTokenInterceptor
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagCacheInterceptor
import com.yumiru11.githubapp.core.githubrest.http.EtagScopeProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagStore
import com.yumiru11.githubapp.core.githubrest.http.GitHubHeaderInterceptor
import com.yumiru11.githubapp.core.githubrest.http.GuestEtagScopeProvider
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
     *
     * [scopeProvider] 追加在末尾并带默认值：既有调用点（大量测试的 `(tokenProvider, etagStore, debugLogging)`
     * 位置参数）保持不变；生产由装配层注入 [com.yumiru11.githubapp.core.githubrest.http.TokenEtagScopeProvider]。
     */
    fun createOkHttpClient(
        tokenProvider: TokenProvider,
        etagStore: EtagStore,
        debugLogging: Boolean,
        scopeProvider: EtagScopeProvider = GuestEtagScopeProvider,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(EtagCacheInterceptor(etagStore, scopeProvider = scopeProvider))
            .addInterceptor(GitHubHeaderInterceptor())
            .addInterceptor(AuthTokenInterceptor(tokenProvider))
            .addInterceptor(
                HttpLoggingInterceptor { message -> Log.d(LOG_TAG, LogRedaction.redact(message)) }.apply {
                    level = if (debugLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                },
            ).connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * 无认证 Retrofit：**第三方 host 专用**（L08 Trending 镜像站）。
     *
     * 刻意不接 OkHttpClient 参数——共享客户端装着 [AuthTokenInterceptor]，
     * 它按 host 无差别注入 `Authorization: Bearer {token}`；GitHub 令牌只应发往
     * api.github.com，发给社区静态镜像属泄漏。超时收紧到 5s（超时即降级）。
     */
    fun createUnauthenticatedRetrofit(
        baseUrl: HttpUrl,
        timeoutSeconds: Long = UNAUTHENTICATED_TIMEOUT_SECONDS,
    ): Retrofit =
        createRetrofit(
            baseUrl = baseUrl,
            client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .build(),
            // Json 在工厂内部构造：消费方（feature 模块）无需依赖 kotlinx-serialization
            json = createJson(),
        )

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

    /** 无认证通道超时（Trending 镜像：超时即走搜索回退，不拖住首页） */
    private const val UNAUTHENTICATED_TIMEOUT_SECONDS = 5L

    /** OkHttp 日志 tag（脱敏后输出，见下方 logger 装配） */
    private const val LOG_TAG = "GitHubRest"
}
