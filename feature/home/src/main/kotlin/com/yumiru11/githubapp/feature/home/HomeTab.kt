package com.yumiru11.githubapp.feature.home

import androidx.annotation.StringRes
import com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons

/**
 * 首页分区枚举（2026-10 UI 重构：对齐 GitHubM3E 的 5 个分区）。
 *
 * 顺序即分区条顺序与 Pager 页序；[icon] 走 [AppIconSpec]，选中态自动用实心变体（空心↔实心）。
 */
enum class HomeTab(
    @StringRes val titleRes: Int,
    val icon: AppIconSpec,
) {
    FEED(R.string.home_tab_feed, AppIcons.Timeline),
    ISSUES(R.string.home_tab_issues, AppIcons.Inbox),
    PULL_REQUESTS(R.string.home_tab_pull_requests, AppIcons.TrendingUp),
    DISCUSSIONS(R.string.home_tab_discussions, AppIcons.Forum),
    EXPLORE(R.string.home_tab_explore, AppIcons.LocalFireDepartment),
}
