package com.yumiru11.githubapp.feature.home

import androidx.annotation.StringRes

/** 首页分区枚举（动态/Issue/PR）——文案走字符串资源（i18n 铁律） */
internal enum class HomeTab(
    @StringRes val titleRes: Int,
) {
    FEED(R.string.home_tab_feed),
    ISSUES(R.string.home_tab_issues),
    PULL_REQUESTS(R.string.home_tab_pull_requests),
}
