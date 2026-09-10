package com.yumiru11.githubapp.core.githubrest.http

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Response
import kotlin.math.ceil

/**
 * 一次 GitHub 限流快照（plan.md §9.3「限流处理 + 结果缓存」）。
 *
 * 数据源：响应头 `x-ratelimit-limit` / `x-ratelimit-remaining` / `x-ratelimit-reset` /
 * `x-ratelimit-resource`。搜索接口的 resource 为 `search`（配额远小于 core），
 * 因此 [resource] 必须随快照一起暴露，UI 才能正确解释"剩余多少"。
 */
data class RateLimitSnapshot(
    /** 该资源的窗口配额 */
    val limit: Int,
    /** 窗口内剩余请求数 */
    val remaining: Int,
    /** 窗口重置时间（Unix epoch 秒，GitHub 语义） */
    val resetEpochSeconds: Long,
    /** 配额资源名（core / search / graphql / integration_manifest …） */
    val resource: String? = null,
) {
    /**
     * 距重置还剩多少分钟（向上取整；已过重置时间返回 0）。
     *
     * 纯函数（[nowMillis] 注入）便于单测。
     */
    fun resetInMinutes(nowMillis: Long = System.currentTimeMillis()): Long {
        val seconds = resetEpochSeconds - nowMillis / MILLIS_PER_SECOND
        if (seconds <= 0) return 0
        return ceil(seconds.toDouble() / SECONDS_PER_MINUTE).toLong()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val SECONDS_PER_MINUTE = 60.0
    }
}

/**
 * 限流快照存储（与 [EtagStore] 同层：core:github-rest 的 http 包，纯网络层，不依赖 UI）。
 *
 * 写入方 = [EtagCacheInterceptor]（它是最外层拦截器，能看到每一条响应）；
 * 读取方 = feature 层（如 feature:search 的限流提示条），经 Hilt 绑定注入。
 */
interface RateLimitStore {
    /** 最近一次观测到的限流快照（未观测到任何带限流头的响应时为 null） */
    val snapshot: StateFlow<RateLimitSnapshot?>

    /** 记录一次观测（值相同不会重复发射：StateFlow 自带 conflate 语义） */
    fun record(snapshot: RateLimitSnapshot)
}

/** 线程安全的进程内限流快照存储（单值覆盖，无历史） */
class InMemoryRateLimitStore : RateLimitStore {
    private val state = MutableStateFlow<RateLimitSnapshot?>(null)

    override val snapshot: StateFlow<RateLimitSnapshot?> = state.asStateFlow()

    override fun record(snapshot: RateLimitSnapshot) {
        state.value = snapshot
    }
}

/**
 * 进程级限流快照单例。
 *
 * 为什么是 object 而不是纯 DI 实例：[EtagCacheInterceptor] 由 `GitHubRestClient.createOkHttpClient`
 * 直接构造（core:github-rest 的 api/di 包在 issue #165 第一部分的文件边界之外，避免与并行改动冲突），
 * 拦截器无法从 Hilt 拿到注入实例。故录制点用进程级对象，Hilt 绑定
 * （[com.yumiru11.githubapp.core.githubrest.http.RateLimitModule]）返回同一个实例，
 * 消费方仍按接口注入（单测可换成 [InMemoryRateLimitStore]）。
 *
 * 后续把限流录制拆成独立拦截器并由 GitHubRestClient 装配时，本对象可直接删除。
 */
object ProcessRateLimitStore : RateLimitStore {
    private val delegate = InMemoryRateLimitStore()

    override val snapshot: StateFlow<RateLimitSnapshot?> get() = delegate.snapshot

    override fun record(snapshot: RateLimitSnapshot) {
        delegate.record(snapshot)
    }
}

/**
 * 从响应头解析限流快照；缺头或数值非法 → null（限流观测永远不能影响正常请求链路）。
 */
fun Response.toRateLimitSnapshot(): RateLimitSnapshot? {
    val limit = header(HEADER_LIMIT)?.toIntOrNull() ?: return null
    val remaining = header(HEADER_REMAINING)?.toIntOrNull() ?: return null
    val reset = header(HEADER_RESET)?.toLongOrNull() ?: return null
    return RateLimitSnapshot(
        limit = limit,
        remaining = remaining,
        resetEpochSeconds = reset,
        resource = header(HEADER_RESOURCE),
    )
}

/** GitHub 限流响应头名（plan.md §4.3/§9.3） */
const val HEADER_LIMIT: String = "x-ratelimit-limit"

/** @see HEADER_LIMIT */
const val HEADER_REMAINING: String = "x-ratelimit-remaining"

/** @see HEADER_LIMIT */
const val HEADER_RESET: String = "x-ratelimit-reset"

/** @see HEADER_LIMIT */
const val HEADER_RESOURCE: String = "x-ratelimit-resource"
