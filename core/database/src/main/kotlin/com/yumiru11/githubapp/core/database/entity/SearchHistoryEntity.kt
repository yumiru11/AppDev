package com.yumiru11.githubapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 搜索历史实体（plan.md §4.6：搜索历史存 Room）。
 *
 * [query] 为主键：同一搜索词重复提交只更新 [updatedAt]（去重，最新优先排序）。
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val updatedAt: Long,
)
