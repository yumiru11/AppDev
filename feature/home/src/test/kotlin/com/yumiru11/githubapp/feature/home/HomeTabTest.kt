package com.yumiru11.githubapp.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页分区枚举契约测试。
 *
 * - entries 顺序 = HomeTabBar（PrimaryTabRow）的展示顺序，变更需同步 ui-design.md §2.1
 * - titleRes 指向分区字符串资源（i18n 铁律：文案不落代码）
 */
class HomeTabTest {
    @Test
    fun homeTab_entries_orderIsFeedIssuesThenPullRequests() {
        assertEquals(
            listOf(HomeTab.FEED, HomeTab.ISSUES, HomeTab.PULL_REQUESTS),
            HomeTab.entries,
        )
    }

    @Test
    fun homeTab_valueOf_roundTripsEveryEntryName() {
        HomeTab.entries.forEach { tab ->
            assertEquals(tab, HomeTab.valueOf(tab.name))
        }
    }

    @Test
    fun homeTab_titleRes_pointsToSubTabStringResources() {
        assertEquals(R.string.home_tab_feed, HomeTab.FEED.titleRes)
        assertEquals(R.string.home_tab_issues, HomeTab.ISSUES.titleRes)
        assertEquals(R.string.home_tab_pull_requests, HomeTab.PULL_REQUESTS.titleRes)
    }
}
