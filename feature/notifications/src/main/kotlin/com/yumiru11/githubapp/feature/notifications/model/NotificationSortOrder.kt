package com.yumiru11.githubapp.feature.notifications.model

/**
 * 通知组内时间排序（L12 / ui-design.md §3.4 A3-2 拍板：按仓库分组 + 组内时间排序）。
 *
 * 记忆语义：分组永远按仓库（不变），本枚举只决定**组内**与**组间**的时间方向，
 * 切换为纯内存态即时重排，不重拉快照。
 */
enum class NotificationSortOrder {
    /** 最新在前（默认；里程碑/评审等按时间倒序阅读） */
    NEWEST_FIRST,

    /** 最旧在前（按时间正序回溯同一仓库的完整脉络） */
    OLDEST_FIRST,
}
