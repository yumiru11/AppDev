package com.yumiru11.githubapp.core.githubrest.api

import java.time.LocalDate

/**
 * Trending 数据源常量与 URL 构造（ui-design.md §2.5 / ADR-0006 §7 拍板方案）。
 *
 * GitHub **官方无 Trending API**，镜像站是社区维护的静态 JSON
 * （`raw.githubusercontent.com/Unpublished/GithubTrending`，与网页 Trending 一致、免 token）。
 * 镜像不可用时回退搜索 API（[fallbackQuery]）。
 *
 * 纯函数（URL/查询串），无网络依赖，可纯 JVM 单测。
 */
object TrendSource {
    /** 镜像 raw 根路径（注意：**不是** api.github.com，请求走 @Url 全量地址） */
    const val MIRROR_BASE_URL: String = "https://raw.githubusercontent.com/Unpublished/GithubTrending/"

    /** 镜像周期（每日/每周/每月榜；`-all` 后缀 = 全语言聚合榜） */
    enum class Period(
        val segment: String,
    ) {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly"),
    }

    /** 镜像榜单 JSON 地址：`{base}trends/trending_{period}-all.json` */
    fun mirrorUrl(period: Period = Period.DAILY): String = "${MIRROR_BASE_URL}trends/trending_${period.segment}-all.json"

    /** 镜像失效时的回退查询串：近 7 天新建 + 星标 > 100（§2.5 拍板） */
    fun fallbackQuery(today: LocalDate): String = "created:>${today.minusDays(FALLBACK_WINDOW_DAYS)} stars:>$FALLBACK_MIN_STARS"

    private const val FALLBACK_WINDOW_DAYS = 7L
    private const val FALLBACK_MIN_STARS = 100
}
