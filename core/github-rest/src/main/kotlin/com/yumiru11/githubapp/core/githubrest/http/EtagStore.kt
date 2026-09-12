package com.yumiru11.githubapp.core.githubrest.http

import java.util.concurrent.ConcurrentHashMap

/**
 * 一条 ETag 缓存记录：响应体 + ETag + 内容类型（回放 304 用）。
 */
data class EtagEntry(
    val etag: String,
    val contentType: String?,
    val body: String,
)

/**
 * ETag 缓存存储抽象。
 *
 * 键为三元组 `(scope, method, url)`：
 * - [scope]：账号作用域——同一凭据才能命中同一分区，保证一个账号读不到另一个账号的缓存体
 *   （见 [EtagScopeProvider]）。
 * - [method]：HTTP 方法（当前仅 GET 会入缓存，仍显式入键，避免未来写方法误命中）。
 * - [url]：完整请求 URL 字符串（含 query，与原实现一致）。
 *
 * 默认提供进程内实现 [InMemoryEtagStore]；跨进程持久化实现见 `core:github-data`
 * 的 `RoomEtagStore`（plan.md §4.6 / 残余审计 DATA-1）。
 *
 * **[clearSessionCache] 不在本接口**：清空是会话生命周期动作，由
 * `core:github-auth` 的 `SessionCacheCleaner` 表达，避免网络层依赖认证层。
 */
interface EtagStore {
    fun get(
        scope: String,
        method: String,
        url: String,
    ): EtagEntry?

    fun put(
        scope: String,
        method: String,
        url: String,
        entry: EtagEntry,
    )
}

/** 线程安全的进程内 ETag 缓存（测试/降级用；重启即失效）。 */
class InMemoryEtagStore : EtagStore {
    private val entries = ConcurrentHashMap<EtagCacheKey, EtagEntry>()

    override fun get(
        scope: String,
        method: String,
        url: String,
    ): EtagEntry? = entries[EtagCacheKey(scope, method, url)]

    override fun put(
        scope: String,
        method: String,
        url: String,
        entry: EtagEntry,
    ) {
        entries[EtagCacheKey(scope, method, url)] = entry
    }

    private data class EtagCacheKey(
        val scope: String,
        val method: String,
        val url: String,
    )
}
