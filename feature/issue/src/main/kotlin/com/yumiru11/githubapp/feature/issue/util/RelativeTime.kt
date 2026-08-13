package com.yumiru11.githubapp.feature.issue.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.yumiru11.githubapp.feature.issue.R
import java.time.Duration
import java.time.Instant

/**
 * 相对时间单位
 */
internal enum class RelativeTimeUnit {
    MINUTES,
    HOURS,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

/** 相对时间结果：JustNow 或 Ago(数量+单位) */
internal sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Ago(
        val count: Int,
        val unit: RelativeTimeUnit,
    ) : RelativeTime
}

/**
 * ISO 时间戳 → 相对时间。
 * 解析失败或时间在未来（异常数据）返回 null，由调用方回退到原样时间。
 */
internal fun computeRelativeTime(
    isoTimestamp: String,
    now: Instant,
): RelativeTime? {
    val then = runCatching { Instant.parse(isoTimestamp) }.getOrNull() ?: return null
    val minutes = Duration.between(then, now).toMinutes()
    if (minutes < 0) return null
    return when {
        minutes < 1 -> RelativeTime.JustNow
        minutes < 60 -> RelativeTime.Ago(minutes.toInt(), RelativeTimeUnit.MINUTES)
        minutes < 60 * 24 -> RelativeTime.Ago((minutes / 60).toInt(), RelativeTimeUnit.HOURS)
        minutes < 60 * 24 * 7 -> RelativeTime.Ago((minutes / (60 * 24)).toInt(), RelativeTimeUnit.DAYS)
        minutes < 60 * 24 * 30 -> RelativeTime.Ago((minutes / (60 * 24 * 7)).toInt(), RelativeTimeUnit.WEEKS)
        minutes < 60 * 24 * 365 -> RelativeTime.Ago((minutes / (60 * 24 * 30)).toInt(), RelativeTimeUnit.MONTHS)
        else -> RelativeTime.Ago((minutes / (60 * 24 * 365)).toInt(), RelativeTimeUnit.YEARS)
    }
}

/** ISO 时间戳 → 本地化相对时间文案；解析失败/未来时间返回 null（调用方回退） */
@Composable
internal fun relativeTimeText(isoTimestamp: String): String? {
    val rel = computeRelativeTime(isoTimestamp, Instant.now()) ?: return null
    return when (rel) {
        RelativeTime.JustNow -> {
            stringResource(R.string.time_just_now)
        }

        is RelativeTime.Ago -> {
            when (rel.unit) {
                RelativeTimeUnit.MINUTES -> pluralStringResource(R.plurals.time_minutes, rel.count, rel.count)
                RelativeTimeUnit.HOURS -> pluralStringResource(R.plurals.time_hours, rel.count, rel.count)
                RelativeTimeUnit.DAYS -> pluralStringResource(R.plurals.time_days, rel.count, rel.count)
                RelativeTimeUnit.WEEKS -> pluralStringResource(R.plurals.time_weeks, rel.count, rel.count)
                RelativeTimeUnit.MONTHS -> pluralStringResource(R.plurals.time_months, rel.count, rel.count)
                RelativeTimeUnit.YEARS -> pluralStringResource(R.plurals.time_years, rel.count, rel.count)
            }
        }
    }
}
