package com.yumiru11.githubapp.feature.issue.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * [computeRelativeTime] 纯函数单测：各时间单位边界 + 非法/未来时间回退。
 */
class RelativeTimeTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun computeRelativeTime_underOneMinute_returnsJustNow() {
        val result = computeRelativeTime("2026-01-01T11:59:30Z", now)

        assertEquals(RelativeTime.JustNow, result)
    }

    @Test
    fun computeRelativeTime_minutes_returnsMinutes() {
        val result = computeRelativeTime("2026-01-01T11:30:00Z", now)

        assertEquals(RelativeTime.Ago(30, RelativeTimeUnit.MINUTES), result)
    }

    @Test
    fun computeRelativeTime_hours_returnsHours() {
        val result = computeRelativeTime("2026-01-01T09:00:00Z", now)

        assertEquals(RelativeTime.Ago(3, RelativeTimeUnit.HOURS), result)
    }

    @Test
    fun computeRelativeTime_days_returnsDays() {
        val result = computeRelativeTime("2025-12-30T12:00:00Z", now)

        assertEquals(RelativeTime.Ago(2, RelativeTimeUnit.DAYS), result)
    }

    @Test
    fun computeRelativeTime_weeks_returnsWeeks() {
        val result = computeRelativeTime("2025-12-11T12:00:00Z", now)

        assertEquals(RelativeTime.Ago(3, RelativeTimeUnit.WEEKS), result)
    }

    @Test
    fun computeRelativeTime_months_returnsMonths() {
        val result = computeRelativeTime("2025-11-01T12:00:00Z", now)

        // 61 天 ≈ 2 个月（按 30 天/月）
        assertEquals(RelativeTime.Ago(2, RelativeTimeUnit.MONTHS), result)
    }

    @Test
    fun computeRelativeTime_years_returnsYears() {
        val result = computeRelativeTime("2023-01-01T12:00:00Z", now)

        assertEquals(RelativeTime.Ago(3, RelativeTimeUnit.YEARS), result)
    }

    @Test
    fun computeRelativeTime_invalidTimestamp_returnsNull() {
        assertNull(computeRelativeTime("not-a-date", now))
    }

    @Test
    fun computeRelativeTime_futureTimestamp_returnsNull() {
        assertNull(computeRelativeTime("2027-01-01T12:00:00Z", now))
    }

    @Test
    fun computeRelativeTime_zeroSeconds_returnsJustNow() {
        assertEquals(RelativeTime.JustNow, computeRelativeTime(now.toString(), now))
    }

    @Test
    fun computeRelativeTime_59Seconds_returnsJustNow() {
        val result = computeRelativeTime(now.minusSeconds(59).toString(), now)

        // Duration.toMinutes() 向下截断：59s → 0 分钟
        assertEquals(RelativeTime.JustNow, result)
    }

    @Test
    fun computeRelativeTime_60Seconds_returnsOneMinute() {
        val result = computeRelativeTime(now.minusSeconds(60).toString(), now)

        assertEquals(RelativeTime.Ago(1, RelativeTimeUnit.MINUTES), result)
    }

    @Test
    fun computeRelativeTime_59Minutes_returns59Minutes() {
        val result = computeRelativeTime(now.minusSeconds(59 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(59, RelativeTimeUnit.MINUTES), result)
    }

    @Test
    fun computeRelativeTime_60Minutes_returnsOneHour() {
        val result = computeRelativeTime(now.minusSeconds(60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(1, RelativeTimeUnit.HOURS), result)
    }

    @Test
    fun computeRelativeTime_23Hours_returns23Hours() {
        val result = computeRelativeTime(now.minusSeconds(23 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(23, RelativeTimeUnit.HOURS), result)
    }

    @Test
    fun computeRelativeTime_24Hours_returnsOneDay() {
        val result = computeRelativeTime(now.minusSeconds(24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(1, RelativeTimeUnit.DAYS), result)
    }

    @Test
    fun computeRelativeTime_6Days_returns6Days() {
        val result = computeRelativeTime(now.minusSeconds(6 * 24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(6, RelativeTimeUnit.DAYS), result)
    }

    @Test
    fun computeRelativeTime_7Days_returnsOneWeek() {
        val result = computeRelativeTime(now.minusSeconds(7 * 24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(1, RelativeTimeUnit.WEEKS), result)
    }

    @Test
    fun computeRelativeTime_29Days_returns4Weeks() {
        val result = computeRelativeTime(now.minusSeconds(29 * 24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(4, RelativeTimeUnit.WEEKS), result)
    }

    @Test
    fun computeRelativeTime_30Days_returnsOneMonth() {
        val result = computeRelativeTime(now.minusSeconds(30 * 24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(1, RelativeTimeUnit.MONTHS), result)
    }

    @Test
    fun computeRelativeTime_364Days_returns12Months() {
        val result = computeRelativeTime(now.minusSeconds(364 * 24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(12, RelativeTimeUnit.MONTHS), result)
    }

    @Test
    fun computeRelativeTime_365Days_returnsOneYear() {
        val result = computeRelativeTime(now.minusSeconds(365 * 24 * 60 * 60).toString(), now)

        assertEquals(RelativeTime.Ago(1, RelativeTimeUnit.YEARS), result)
    }

    @Test
    fun computeRelativeTime_futureByClockSkew_returnsNull() {
        // 时钟偏差 30s（now 早于 then）→ Duration 为负 → 回退
        assertNull(computeRelativeTime(now.plusSeconds(30).toString(), now))
    }

    @Test
    fun computeRelativeTime_emptyString_returnsNull() {
        assertNull(computeRelativeTime("", now))
    }

    @Test
    fun computeRelativeTime_isoWithOffset_returnsRelativeTime() {
        // Instant.parse 支持带时区偏移的 ISO-8601（GitHub API 返回格式）
        val result = computeRelativeTime("2026-01-01T11:00:00+08:00", now)

        // +08:00 的 11:00 = UTC 03:00 → 距 now 9 小时
        assertEquals(RelativeTime.Ago(9, RelativeTimeUnit.HOURS), result)
    }
}
