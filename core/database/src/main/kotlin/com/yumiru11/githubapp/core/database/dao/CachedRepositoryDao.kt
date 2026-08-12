package com.yumiru11.githubapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yumiru11.githubapp.core.database.entity.CachedRepositoryEntity

/**
 * 仓库缓存 DAO（ETag 304 离线缓存的数据出入口）。
 */
@Dao
interface CachedRepositoryDao {
    /** 插入或覆盖（复合主键 owner+name） */
    @Upsert
    suspend fun upsert(entity: CachedRepositoryEntity)

    @Query("SELECT * FROM cached_repositories WHERE owner = :owner AND name = :name")
    suspend fun getByOwnerAndName(
        owner: String,
        name: String,
    ): CachedRepositoryEntity?

    /** 全部缓存，按最近更新倒序 */
    @Query("SELECT * FROM cached_repositories ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CachedRepositoryEntity>

    @Query("DELETE FROM cached_repositories WHERE owner = :owner AND name = :name")
    suspend fun delete(
        owner: String,
        name: String,
    )

    @Query("DELETE FROM cached_repositories")
    suspend fun clear()
}
