package com.yumiru11.githubapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yumiru11.githubapp.core.database.entity.EtagCacheEntity

/**
 * 持久化 ETag 缓存 DAO（主键 scope+method+url）。
 *
 * 驱逐辅助查询（[deleteExpired] / [count] / [totalBytes] / [deleteLeastRecentlyUsed] /
 * [deleteOtherScopes]）供 `RoomEtagStore` 维护 TTL 与容量上界、以及单账号落盘不变量。
 */
@Dao
interface EtagCacheDao {
    @Query("SELECT * FROM etag_cache WHERE scope = :scope AND method = :method AND url = :url")
    suspend fun get(
        scope: String,
        method: String,
        url: String,
    ): EtagCacheEntity?

    @Upsert
    suspend fun upsert(entity: EtagCacheEntity)

    @Query("DELETE FROM etag_cache WHERE scope = :scope AND method = :method AND url = :url")
    suspend fun delete(
        scope: String,
        method: String,
        url: String,
    )

    @Query("UPDATE etag_cache SET lastAccessedAt = :at WHERE scope = :scope AND method = :method AND url = :url")
    suspend fun touch(
        scope: String,
        method: String,
        url: String,
        at: Long,
    )

    /** 清空全部作用域（登出/切换账号）。 */
    @Query("DELETE FROM etag_cache")
    suspend fun clear()

    /** 仅保留 [scope]（切账号后清掉旧账号残留的私有正文）。 */
    @Query("DELETE FROM etag_cache WHERE scope != :scope")
    suspend fun deleteOtherScopes(scope: String)

    @Query("DELETE FROM etag_cache WHERE storedAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)

    @Query("SELECT COUNT(*) FROM etag_cache")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(bodyBytes), 0) FROM etag_cache")
    suspend fun totalBytes(): Long

    /** 删除最近最少使用（[lastAccessedAt] 升序）的 [count] 条。 */
    @Query(
        "DELETE FROM etag_cache WHERE rowid IN " +
            "(SELECT rowid FROM etag_cache ORDER BY lastAccessedAt ASC LIMIT :count)",
    )
    suspend fun deleteLeastRecentlyUsed(count: Int)

    @Query("SELECT * FROM etag_cache WHERE scope = :scope ORDER BY lastAccessedAt DESC")
    suspend fun getByScope(scope: String): List<EtagCacheEntity>
}
