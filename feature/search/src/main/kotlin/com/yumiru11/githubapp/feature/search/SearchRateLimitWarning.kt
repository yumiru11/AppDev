package com.yumiru11.githubapp.feature.search

import androidx.compose.runtime.Immutable
import com.yumiru11.githubapp.core.githubrest.http.RateLimitSnapshot

/**
 * 搜索限流偏低提示（issue #165 / L13，plan.md §9.3「限流处理 + 结果缓存」）。
 *
 * [remaining] / [resetInMinutes] 均为数值，文案由 UI 层 stringResource 本地化
 * （ViewModel 不产英文，同 SearchErrorType 约定）。
 */
@Immutable
data class SearchRateLimitWarning(
    /** 当前窗口剩余请求数 */
    val remaining: Int,
    /** 距配额重置的剩余分钟数（向上取整） */
    val resetInMinutes: Long,
)

/**
 * core 配额（5000/h）下触发提示的剩余请求数阈值（plan.md §9.3 建议值 50）。
 */
const val RATE_LIMIT_WARNING_THRESHOLD: Int = 50

/** 小配额资源（search 认证 30/min、未认证 10/min）使用的比例阈值 */
private const val SMALL_QUOTA_RATIO: Double = 0.2

/** 小配额资源的阈值下限（避免"剩 1 次才提示"） */
private const val MIN_WARNING_THRESHOLD: Int = 3

/**
 * 生效阈值：配额足够大（≥ 2 × [RATE_LIMIT_WARNING_THRESHOLD]）时用绝对值 50；
 * 小配额资源改用 20% 比例（下限 3）——否则搜索接口（limit=30）会永远顶着提示条。
 */
internal fun rateLimitWarningThreshold(limit: Int): Int =
    if (limit >= RATE_LIMIT_WARNING_THRESHOLD * 2) {
        RATE_LIMIT_WARNING_THRESHOLD
    } else {
        (limit * SMALL_QUOTA_RATIO).toInt().coerceAtLeast(MIN_WARNING_THRESHOLD)
    }

/** 快照 → 提示（未低于阈值返回 null = 不展示提示条）；[nowMillis] 注入便于单测 */
internal fun RateLimitSnapshot.toWarning(nowMillis: Long = System.currentTimeMillis()): SearchRateLimitWarning? =
    if (remaining <= rateLimitWarningThreshold(limit)) {
        SearchRateLimitWarning(remaining = remaining, resetInMinutes = resetInMinutes(nowMillis))
    } else {
        null
    }
