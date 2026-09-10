package com.yumiru11.githubapp.feature.search

import com.yumiru11.githubapp.core.githubrest.http.RateLimitSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 限流提示阈值与相对时间单测（issue #165 / L13，plan.md §9.3）。
 *
 * 阈值策略：大配额（core 5000/h）用绝对值 50；小配额（search 30/min、10/min）用 20% 比例
 * （下限 3），否则搜索接口会永远顶着提示条。
 */
class SearchRateLimitTest {
    @Test
    fun rateLimitWarningThreshold_largeQuota_returnsSpecValue() {
        assertEquals(RATE_LIMIT_WARNING_THRESHOLD, rateLimitWarningThreshold(limit = 5000))
        assertEquals(RATE_LIMIT_WARNING_THRESHOLD, rateLimitWarningThreshold(limit = 100))
    }

    @Test
    fun rateLimitWarningThreshold_searchQuota_usesProportionalValue() {
        assertEquals(6, rateLimitWarningThreshold(limit = 30))
        assertEquals(3, rateLimitWarningThreshold(limit = 10))
    }

    @Test
    fun rateLimitWarningThreshold_tinyQuota_neverBelowFloor() {
        assertEquals(3, rateLimitWarningThreshold(limit = 1))
        assertEquals(3, rateLimitWarningThreshold(limit = 0))
    }

    @Test
    fun toWarning_coreQuotaAtThreshold_returnsWarning() {
        val warning = snapshot(limit = 5000, remaining = 50).toWarning(nowMillis = 0L)

        assertEquals(50, warning?.remaining)
    }

    @Test
    fun toWarning_coreQuotaAboveThreshold_returnsNull() {
        assertNull(snapshot(limit = 5000, remaining = 51).toWarning(nowMillis = 0L))
    }

    @Test
    fun toWarning_searchQuotaAboveProportionalThreshold_returnsNull() {
        assertNull(snapshot(limit = 30, remaining = 7).toWarning(nowMillis = 0L))
    }

    @Test
    fun toWarning_searchQuotaAtProportionalThreshold_returnsWarning() {
        assertEquals(6, snapshot(limit = 30, remaining = 6).toWarning(nowMillis = 0L)?.remaining)
    }

    @Test
    fun toWarning_reportsResetInMinutesRoundedUp() {
        // 当前 1_800_000_000 秒（毫秒 = 1_800_000_000_000），重置落在 90 秒后
        val warning =
            snapshot(limit = 5000, remaining = 1, resetEpochSeconds = 1_800_000_090L)
                .toWarning(nowMillis = 1_800_000_000_000L)

        assertEquals(2L, warning?.resetInMinutes)
    }

    @Test
    fun toWarning_resetAlreadyPassed_reportsZeroMinutes() {
        val warning =
            snapshot(limit = 5000, remaining = 1, resetEpochSeconds = 1_799_999_000L)
                .toWarning(nowMillis = 1_800_000_000_000L)

        assertEquals(0L, warning?.resetInMinutes)
    }

    private fun snapshot(
        limit: Int,
        remaining: Int,
        resetEpochSeconds: Long = 1_800_000_000L,
        resource: String = "search",
    ): RateLimitSnapshot =
        RateLimitSnapshot(limit = limit, remaining = remaining, resetEpochSeconds = resetEpochSeconds, resource = resource)
}
