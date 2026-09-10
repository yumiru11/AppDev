@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// 第三方镜像 + 搜索回退全部静默降级：Trending 是 feed 的加分内容，
// 任一失败（超时/网络/解析/限流）都不得抛出打断首页（ui-design §2.5 / L08 验收）。
// SwallowedException 与 TooGenericExceptionCaught 同源：异常本身**按设计**不作为
// 用户可见信息（无日志设施、无错误块），降级结果即空列表 → UI 隐藏小节；
// 调用方取消仍原样上抛（见各处 CancellationException 分支）。

package com.yumiru11.githubapp.feature.home.data

import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import com.yumiru11.githubapp.core.githubrest.api.TrendApi
import com.yumiru11.githubapp.core.githubrest.api.TrendSource
import com.yumiru11.githubapp.feature.home.model.TrendItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trending 数据仓库（L08 / ui-design.md §2.5）：镜像优先 → 搜索回退 → 6h 缓存。
 *
 * 链路：
 * 1. **镜像**（[TrendApi] + [TrendSource.mirrorUrl]）：与 GitHub 网页 Trending 一致，免 token
 * 2. **回退**（[SearchApi] `created:>7d stars:>100`，sort=stars）：镜像超时/不可用时兜底
 * 3. **缓存**：命中 6h 内结果直接返回；两路都失败时回退到过期缓存（有旧数据看旧数据）
 *
 * 契约：**永不抛出**（调用方取消除外）。全链路失败返回空列表 → UI 静默隐藏 Trending 小节。
 * 单次请求超时 [REQUEST_TIMEOUT_MILLIS]（5s），超时即降级，不阻塞 feed。
 */
@Singleton
class TrendRepository
    internal constructor(
        private val trendApi: TrendApi,
        private val searchApi: SearchApi,
        private val now: () -> Long,
    ) {
        /** Hilt 装配入口（测试经 internal 主构造注入假时钟） */
        @Inject
        constructor(
            trendApi: TrendApi,
            searchApi: SearchApi,
        ) : this(trendApi, searchApi, System::currentTimeMillis)

        private var cache: CachedTrends? = null

        /**
         * 拉取 Trending 榜单（默认每日榜），失败返回空列表（UI 隐藏小节）。
         */
        suspend fun trending(period: TrendSource.Period = TrendSource.Period.DAILY): List<TrendItem> {
            val timestamp = now()
            cache?.takeIf { it.period == period && timestamp - it.atMillis < CACHE_TTL_MILLIS }?.let { return it.items }

            val fresh = fetchMirror(period) ?: fetchSearchFallback(timestamp)
            if (fresh == null) return cache?.items.orEmpty()

            cache = CachedTrends(period = period, atMillis = timestamp, items = fresh)
            return fresh
        }

        /** 镜像路：非空才认成功（空榜单/脏数据视同失败，交回退兜底） */
        private suspend fun fetchMirror(period: TrendSource.Period): List<TrendItem>? =
            withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
                try {
                    trendApi
                        .trending(TrendSource.mirrorUrl(period))
                        .mapNotNull { it.toTrendItem() }
                        .take(MAX_ITEMS)
                        .ifEmpty { null }
                } catch (e: CancellationException) {
                    throw e // 超时/调用方取消：交回 withTimeoutOrNull 收口
                } catch (e: Exception) {
                    null
                }
            }

        /** 回退路：搜索 API（created:>7d stars:>100，按 star 排序） */
        private suspend fun fetchSearchFallback(timestamp: Long): List<TrendItem>? =
            withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
                try {
                    val today = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
                    searchApi
                        .searchRepositories(
                            query = TrendSource.fallbackQuery(today),
                            perPage = MAX_ITEMS,
                            sort = SORT_BY_STARS,
                        ).items
                        .map { it.toTrendItem() }
                        .ifEmpty { null }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }

        private data class CachedTrends(
            val period: TrendSource.Period,
            val atMillis: Long,
            val items: List<TrendItem>,
        )

        private companion object {
            /** feed 尾部小节展示条数（避免 Trending 压过动态流主体） */
            const val MAX_ITEMS = 5

            /** 镜像/回退单次请求超时（超时静默降级，绝不阻塞 feed） */
            const val REQUEST_TIMEOUT_MILLIS = 5_000L

            /** 缓存有效期 6h */
            const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1_000L

            const val SORT_BY_STARS = "stars"
        }
    }
