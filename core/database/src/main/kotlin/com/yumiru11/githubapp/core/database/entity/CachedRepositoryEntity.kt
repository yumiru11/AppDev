package com.yumiru11.githubapp.core.database.entity

import androidx.room.Entity

/**
 * 仓库响应缓存实体（ETag 304 离线复用：REST 响应体 + 校验头）。
 *
 * [owner]/[name] 复合主键对应 GitHub 仓库唯一标识；[payload] 为序列化后的
 * REST 响应体 JSON 字符串；[etag] 为空表示响应未带校验头（下次全量请求）。
 */
@Entity(tableName = "cached_repositories", primaryKeys = ["owner", "name"])
data class CachedRepositoryEntity(
    val owner: String,
    val name: String,
    val etag: String?,
    val payload: String,
    val updatedAt: Long,
)
