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
 * ETag 缓存存储抽象（按请求 URL 键控）。
 *
 * 默认提供进程内实现；后续可换 Room 持久化实现实现跨会话缓存（plan.md §4.6）。
 */
interface EtagStore {
    fun get(url: String): EtagEntry?

    fun put(
        url: String,
        entry: EtagEntry,
    )
}

/** 线程安全的进程内 ETag 缓存 */
class InMemoryEtagStore : EtagStore {
    private val entries = ConcurrentHashMap<String, EtagEntry>()

    override fun get(url: String): EtagEntry? = entries[url]

    override fun put(
        url: String,
        entry: EtagEntry,
    ) {
        entries[url] = entry
    }
}
