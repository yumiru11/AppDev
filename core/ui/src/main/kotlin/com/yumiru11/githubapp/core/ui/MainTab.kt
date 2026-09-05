package com.yumiru11.githubapp.core.ui

/**
 * 底部导航三分区键（2026-08-14 分区重构 + #90 路由类型化后与导航路由解耦）。
 *
 * 三分区（首页/仓库/我的）不是 NavHost destination，而是 HOME 内的
 * MainTabPager 横向分页——此处是纯字符串键，与 core:navigation 的 AppRoute
 * 类型安全导航路由互不混用。
 */
object MainTab {
    const val HOME = "home"
    const val REPOS = "repos"
    const val PROFILE = "profile"
}
