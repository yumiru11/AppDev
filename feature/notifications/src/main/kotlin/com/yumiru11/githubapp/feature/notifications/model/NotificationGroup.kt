package com.yumiru11.githubapp.feature.notifications.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * 按仓库聚合的通知分组（#88 面板：按仓库分组 + 组内时间排序，ui-design §3.4）。
 *
 * @param repoFullName 仓库名（owner/repo，分组键）
 * @param items 组内通知；[groupByRepository] 保证 updatedAt 倒序
 */
@Immutable
data class NotificationGroup(
    val repoFullName: String,
    val items: List<NotificationItem>,
) {
    /** 未读数（组头角标） */
    val unreadCount: Int get() = items.count { it.unread }
}

/**
 * 面板分组纯函数：按仓库 groupBy → 组内按 [order] 排序 → 组间按同向的时间基准排序。
 *
 * 分组维度恒为仓库（ui-design §3.4 A3-2），[order] 只翻转时间方向：
 * - [NotificationSortOrder.NEWEST_FIRST]：组内倒序，组间按组内**最新**一条倒序
 * - [NotificationSortOrder.OLDEST_FIRST]：组内正序，组间按组内**最旧**一条正序
 *
 * 时间戳缺失/非法 ISO-8601 视为最旧；排序稳定，同时间戳保持服务端相对顺序。
 * 折叠是 UI 状态（ViewModel collapsedRepos），不在此处理。
 */
fun groupByRepository(
    items: List<NotificationItem>,
    order: NotificationSortOrder = NotificationSortOrder.NEWEST_FIRST,
): List<NotificationGroup> {
    val newestFirst = order == NotificationSortOrder.NEWEST_FIRST
    return items
        .groupBy(NotificationItem::repoFullName)
        .map { (repo, groupItems) ->
            NotificationGroup(
                repoFullName = repo,
                items =
                    if (newestFirst) {
                        groupItems.sortedByDescending(::itemTimestamp)
                    } else {
                        groupItems.sortedBy(::itemTimestamp)
                    },
            )
        }.sortedWith(
            if (newestFirst) {
                compareByDescending<NotificationGroup> { group ->
                    group.items.maxOfOrNull(::itemTimestamp) ?: Long.MIN_VALUE
                }
            } else {
                compareBy<NotificationGroup> { group ->
                    group.items.minOfOrNull(::itemTimestamp) ?: Long.MAX_VALUE
                }
            },
        )
}

/** updatedAt → epoch millis；缺失/非法回退 Long.MIN_VALUE（最旧） */
private fun itemTimestamp(item: NotificationItem): Long =
    item.updatedAt?.let { iso ->
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)
    } ?: Long.MIN_VALUE
