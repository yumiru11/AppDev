package com.yumiru11.githubapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 持久化 ETag 缓存实体（残余审计 DATA-1，plan.md §4.6）。
 *
 * 主键 = `(scope, method, url)`：
 * - [scope]：账号作用域（凭据摘要 / anonymous），保证账号间缓存体不可互读
 * - [method]：HTTP 方法（当前仅 GET 入缓存）
 * - [url]：完整请求 URL（含 query）
 *
 * [bodyBytes] 冗余存正文 UTF-8 字节数，供容量驱逐做聚合查询（避免逐行反序列化）；
 * [storedAt] 供 TTL 过期判定，[lastAccessedAt] 供 LRU 驱逐。
 */
@Entity(
    tableName = "etag_cache",
    primaryKeys = ["scope", "method", "url"],
    indices = [Index("lastAccessedAt"), Index("storedAt")],
)
data class EtagCacheEntity(
    val scope: String,
    val method: String,
    val url: String,
    val etag: String,
    val contentType: String?,
    val body: String,
    val bodyBytes: Long,
    val storedAt: Long,
    val lastAccessedAt: Long,
)
