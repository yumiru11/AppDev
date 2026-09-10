package com.yumiru11.githubapp.feature.search.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 缓存中的一页搜索结果（[SearchResultCache] 的读取结果）。
 */
data class CachedSearchPage<T : Any>(
    val items: List<T>,
    /** 是否还有下一页（GitHub 搜索满页即有下一页，与 SearchPagingSource 一致） */
    val hasMore: Boolean,
    /** 是否已做过一次后台静默刷新（防止 invalidate 后自我触发无限重刷） */
    val revalidated: Boolean,
)

/**
 * 搜索结果内存缓存（issue #165 / L13，plan.md §9.3「限流处理 + 结果缓存」）。
 *
 * 形态：query 级 LRU（[maxQueries] 条 query，默认 20）+ 每条 query 内按页号分片。
 * 搜索 API 限流严格（未认证 10 次/分、认证 30 次/分），重复查询直接命中缓存可显著省配额。
 *
 * 过期：条目写入超过 [ttlMillis]（默认 5 分钟）即视为未命中并被剔除（搜索结果时效性弱，
 * 但也不能无限陈旧）。
 *
 * key 约定：`"${tab.name}:$query"` —— Tab 已编码了结果类型，故同一 key 的泛型 T 恒定，
 * 内部以 List<Any> 存储、读取时按 T 还原（[get] 处的唯一非受检转换点）。
 *
 * 线程安全：全部读写走 [Mutex]；[queries] 用访问序 LinkedHashMap 实现 LRU
 * （get 也会更新访问序，命中即续命）。
 */
class SearchResultCache(
    private val maxQueries: Int = MAX_QUERIES,
    private val ttlMillis: Long = CACHE_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private val queries =
        object : LinkedHashMap<String, MutableMap<Int, Entry>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableMap<Int, Entry>>): Boolean = size > maxQueries
        }

    /** 命中且未过期时返回该页（同时续 LRU 访问序）；未命中/已过期返回 null */
    suspend fun <T : Any> get(
        key: String,
        page: Int,
    ): CachedSearchPage<T>? =
        mutex.withLock {
            val entry = queries[key]?.get(page) ?: return@withLock null
            if (isExpired(entry)) {
                queries[key]?.remove(page)
                return@withLock null
            }
            @Suppress("UNCHECKED_CAST")
            // key 内含 Tab → 同一 key 的 T 恒定（见类 KDoc）
            CachedSearchPage(
                items = entry.items as List<T>,
                hasMore = entry.hasMore,
                revalidated = entry.revalidated,
            )
        }

    /** 写入一页（覆盖同 key 同页） */
    suspend fun <T : Any> put(
        key: String,
        page: Int,
        items: List<T>,
        hasMore: Boolean,
        revalidated: Boolean = false,
    ) {
        mutex.withLock {
            queries.getOrPut(key) { mutableMapOf() }[page] =
                Entry(
                    items = items.toList(),
                    hasMore = hasMore,
                    revalidated = revalidated,
                    cachedAt = clock(),
                )
        }
    }

    /** 丢弃某条 query 的全部页（查询内容变化/显式刷新时用） */
    suspend fun invalidate(key: String) {
        mutex.withLock { queries.remove(key) }
    }

    /** 清空全部缓存 */
    suspend fun clear() {
        mutex.withLock { queries.clear() }
    }

    /** 当前缓存的 query 条数（LRU 上限的观测点） */
    suspend fun queryCount(): Int = mutex.withLock { queries.size }

    /** 某条 query 已缓存的页数 */
    suspend fun pageCount(key: String): Int = mutex.withLock { queries[key]?.size ?: 0 }

    private fun isExpired(entry: Entry): Boolean = clock() - entry.cachedAt > ttlMillis

    private data class Entry(
        val items: List<Any>,
        val hasMore: Boolean,
        val revalidated: Boolean,
        val cachedAt: Long,
    )

    companion object {
        /** LRU 上限：20 条 query（issue #165 验收） */
        const val MAX_QUERIES: Int = 20

        /** 条目存活时间：5 分钟 */
        const val CACHE_TTL_MILLIS: Long = 5 * 60 * 1000L

        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
