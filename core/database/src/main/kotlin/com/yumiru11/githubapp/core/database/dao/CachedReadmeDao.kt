package com.yumiru11.githubapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity

/**
 * README 缓存 DAO（双 key 失效：contentHash + themeVersion）。
 */
@Dao
interface CachedReadmeDao {
    /** 插入或覆盖（复合主键 owner+repo） */
    @Upsert
    suspend fun upsert(entity: CachedReadmeEntity)

    @Query("SELECT * FROM cached_readme WHERE owner = :owner AND repo = :repo")
    suspend fun getByOwnerAndRepo(
        owner: String,
        repo: String,
    ): CachedReadmeEntity?

    /** 全部缓存，按最近更新倒序 */
    @Query("SELECT * FROM cached_readme ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CachedReadmeEntity>

    @Query("DELETE FROM cached_readme WHERE owner = :owner AND repo = :repo")
    suspend fun delete(
        owner: String,
        repo: String,
    )

    @Query("DELETE FROM cached_readme")
    suspend fun clear()
}
