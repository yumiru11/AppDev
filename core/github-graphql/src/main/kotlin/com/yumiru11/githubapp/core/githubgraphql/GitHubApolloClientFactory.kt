package com.yumiru11.githubapp.core.githubgraphql

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.yumiru11.githubapp.core.githubgraphql.generated.cache.Cache
import okhttp3.OkHttpClient

/**
 * ApolloClient 工厂（GraphQL 读优先通道，plan.md §4.4）。
 *
 * - 共享 OkHttp：统一请求头 / Auth / ETag 拦截器链对 GraphQL 同样生效
 *   （ETag 拦截器只处理 GET，POST 无副作用）
 * - Normalized Cache：memory → SQLite 链（Apollo 5 新版独立缓存库 com.apollographql.cache，
 *   cache() builder 扩展由 normalized-cache-apollo-compiler-plugin 生成；
 *   测试环境无 Context，仅 memory）
 * - response-based codegen + 自定义标量映射见 build.gradle.kts 的 mapScalar
 */
object GitHubApolloClientFactory {
    /** 内存缓存上限（仓库/用户元数据量级远小于此） */
    private const val MEMORY_CACHE_MAX_BYTES = 10 * 1024 * 1024

    fun create(
        serverUrl: String,
        okHttpClient: OkHttpClient,
        persistentCacheFactory: NormalizedCacheFactory? = null,
    ): ApolloClient {
        val memoryCache = MemoryCacheFactory(maxSizeBytes = MEMORY_CACHE_MAX_BYTES)
        val cacheFactory = persistentCacheFactory?.let(memoryCache::chain) ?: memoryCache
        // cache() 是 compiler plugin 生成的 Cache object 成员扩展（含 schema 感知的缓存策略）
        return with(Cache) {
            ApolloClient
                .Builder()
                .serverUrl(serverUrl)
                .httpEngine(DefaultHttpEngine(okHttpClient))
                .cache(cacheFactory)
                .build()
        }
    }
}
