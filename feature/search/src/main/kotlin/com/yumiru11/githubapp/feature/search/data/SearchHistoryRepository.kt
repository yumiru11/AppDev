package com.yumiru11.githubapp.feature.search.data

import com.yumiru11.githubapp.core.database.dao.SearchHistoryDao
import com.yumiru11.githubapp.core.database.entity.SearchHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索历史仓库（T18，Room search_history 表）。
 *
 * - [add]：提交搜索时记录（query 主键去重，重复搜索刷新时间戳 → 提到最前）
 * - [recent]：最近 N 条（UI 历史 chips）
 * - [clear]：全部清除
 */
@Singleton
class SearchHistoryRepository
    @Inject
    constructor(
        private val searchHistoryDao: SearchHistoryDao,
    ) {
        suspend fun recent(limit: Int = RECENT_LIMIT): List<String> = searchHistoryDao.getRecent(limit)

        suspend fun add(query: String) {
            searchHistoryDao.upsert(
                SearchHistoryEntity(
                    query = query,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        suspend fun clear() {
            searchHistoryDao.clear()
        }

        private companion object {
            const val RECENT_LIMIT = 20
        }
    }
