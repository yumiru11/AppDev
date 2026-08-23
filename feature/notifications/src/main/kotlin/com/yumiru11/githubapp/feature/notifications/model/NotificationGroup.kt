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
 * 面板分组纯函数：按仓库 groupBy → 组内 updatedAt 倒序 → 组间按组内最新时间倒序。
 *
 * 时间戳缺失/非法 ISO-8601 视为最旧（排组尾/组间末位）；排序稳定，同时间戳保持
 * 服务端相对顺序。折叠是 UI 状态（ViewModel collapsedRepos），不在此处理。
 */
fun groupByRepository(items: List<NotificationItem>): List<NotificationGroup> =
    items
        .groupBy(NotificationItem::repoFullName)
        .map { (repo, groupItems) ->
            NotificationGroup(
                repoFullName = repo,
                items = groupItems.sortedByDescending(::itemTimestamp),
            )
        }.sortedByDescending { group -> group.items.maxOfOrNull(::itemTimestamp) ?: Long.MIN_VALUE }

/** updatedAt → epoch millis；缺失/非法回退 Long.MIN_VALUE（最旧） */
private fun itemTimestamp(item: NotificationItem): Long =
    item.updatedAt?.let { iso ->
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)
    } ?: Long.MIN_VALUE
