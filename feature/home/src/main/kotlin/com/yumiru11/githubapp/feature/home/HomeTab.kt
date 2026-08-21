package com.yumiru11.githubapp.feature.home

/** 首页分区枚举（动态/Issue/PR） */
internal enum class HomeTab(
    val title: String,
) {
    FEED("动态"),
    ISSUES("Issue"),
    PULL_REQUESTS("PR"),
}
