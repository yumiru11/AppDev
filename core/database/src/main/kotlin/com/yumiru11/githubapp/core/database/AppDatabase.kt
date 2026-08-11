package com.yumiru11.githubapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yumiru11.githubapp.core.database.dao.CachedRepositoryDao
import com.yumiru11.githubapp.core.database.entity.CachedRepositoryEntity

/**
 * 应用本地数据库 v1。
 *
 * 当前承载仓库响应缓存（ETag 304）；后续工单按需新增表并 bump version + Migration。
 */
@Database(
    entities = [CachedRepositoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedRepositoryDao(): CachedRepositoryDao
}
