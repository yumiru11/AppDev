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
}
