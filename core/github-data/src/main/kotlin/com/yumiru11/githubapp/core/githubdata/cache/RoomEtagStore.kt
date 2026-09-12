package com.yumiru11.githubapp.core.githubdata.cache

import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.database.entity.EtagCacheEntity
import com.yumiru11.githubapp.core.githubauth.session.SessionCacheCleaner
import com.yumiru11.githubapp.core.githubrest.http.EtagEntry
import com.yumiru11.githubapp.core.githubrest.http.EtagStore
import kotlinx.coroutines.runBlocking

/**
 * ETag 缓存容量/TTL 策略（DATA-1 上界与理由，见 PR）。
 *
 * - [maxEntryBytes] 2 MiB：与拦截器 `MAX_CACHED_BODY_BYTES` 一致，超限单条不入库。
 * - [maxEntries] 500：GitHub JSON 很小（仓库元数据 ~10–100 KiB），500 条足以覆盖一次典型
 *   会话的全部列表/详情去重，同时给 Room 文件一个硬上界。
 * - [maxTotalBytes] 16 MiB：整库正文上界，约等于数百条；移动端磁盘占用可忽略。
 * - [ttlMillis] 7 天：ETag 失效由服务端 304/200 决定，TTL 只负责回收陈旧数据与限制私有正文
 *   在盘上的留存时长；7 天覆盖「隔几天再用」但仍限制长期残留。
 */
data class EtagCachePolicy(
    val maxEntryBytes: Long = 2L * 1024 * 1024,
    val maxEntries: Int = 500,
    val maxTotalBytes: Long = 16L * 1024 * 1024,
    val ttlMillis: Long = 7L * 24 * 60 * 60 * 1000,
)

/**
 * Room 持久化 [EtagStore]（DATA-1）：重启后仍可按 `(scope, method, url)` 命中并复用 304 回放。
 *
 * ## 为什么用 [runBlocking]
 * [EtagStore] 是同步接口（OkHttp 拦截器在 dispatcher 线程同步调用），而 Room DAO 为 suspend；
 * 该桥接只在 OkHttp **非主线程**执行，不会卡 UI；缓存条目小，单次阻塞可忽略。
 *
 * ## 安全不变量
 * 1. 读取严格按 [scope] 分区——一个账号永远读不到另一个账号写入的正文。
 * 2. 单账号落盘：作用域变化后首次写入会清掉其他作用域（切账号即抹掉旧账号私有正文残留）。
 * 3. [clearSessionCache]（登出）删除全表。
 */
class RoomEtagStore(
    private val dao: EtagCacheDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val policy: EtagCachePolicy = EtagCachePolicy(),
) : EtagStore,
    SessionCacheCleaner {
    @Volatile
    private var activeScope: String? = null

    override fun get(
        scope: String,
        method: String,
        url: String,
    ): EtagEntry? =
        runBlocking {
            val row = dao.get(scope, method, url) ?: return@runBlocking null
            val now = clock()
            if (now - row.storedAt >= policy.ttlMillis) {
                dao.delete(scope, method, url)
                return@runBlocking null
            }
            dao.touch(scope, method, url, now)
            EtagEntry(etag = row.etag, contentType = row.contentType, body = row.body)
        }

    override fun put(
        scope: String,
        method: String,
        url: String,
        entry: EtagEntry,
    ) {
        runBlocking {
            val bodyBytes =
                entry.body
                    .toByteArray(Charsets.UTF_8)
                    .size
                    .toLong()
            if (bodyBytes > policy.maxEntryBytes) return@runBlocking
            if (activeScope != scope) {
                dao.deleteOtherScopes(scope)
                activeScope = scope
            }
            val now = clock()
            dao.upsert(
                EtagCacheEntity(
                    scope = scope,
                    method = method,
                    url = url,
                    etag = entry.etag,
                    contentType = entry.contentType,
                    body = entry.body,
                    bodyBytes = bodyBytes,
                    storedAt = now,
                    lastAccessedAt = now,
                ),
            )
            enforceBounds(now)
        }
    }

    override suspend fun clearSessionCache() {
        dao.clear()
        activeScope = null
    }

    private suspend fun enforceBounds(now: Long) {
        dao.deleteExpired(now - policy.ttlMillis)
        val overflow = dao.count() - policy.maxEntries
        if (overflow > 0) dao.deleteLeastRecentlyUsed(overflow)
        var bytes = dao.totalBytes()
        while (bytes > policy.maxTotalBytes && dao.count() > 0) {
            dao.deleteLeastRecentlyUsed(1)
            bytes = dao.totalBytes()
        }
    }
}
