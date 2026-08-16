package com.yumiru11.githubapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yumiru11.githubapp.core.database.entity.SearchHistoryEntity

/**
 * 搜索历史 DAO（插入去重 + 最近倒序 + 清空）。
 */
@Dao
interface SearchHistoryDao {
    /** 插入或覆盖（query 主键去重，重复搜索只刷新时间戳） */
    @Upsert
    suspend fun upsert(entity: SearchHistoryEntity)

    /** 全部历史，最近搜索优先 */
    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    /** 最近 [limit] 条历史，最近搜索优先 */
    @Query("SELECT query FROM search_history ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<String>

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
