package com.yumiru11.githubapp.feature.notifications.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [groupByRepository] 纯函数单测：分组、组内/组间时间排序、非法时间戳兜底、未读计数。
 */
class NotificationGroupTest {
    private fun item(
        id: String,
        repo: String = "octocat/Hello-World",
        updatedAt: String? = "2026-08-01T10:00:00Z",
        unread: Boolean = true,
    ): NotificationItem =
        NotificationItem(
            id = id,
            repoFullName = repo,
            subjectTitle = "title",
            subjectType = "Issue",
            reason = "subscribed",
            unread = unread,
            updatedAt = updatedAt,
            htmlUrl = null,
        )

    @Test
    fun groupByRepository_mixedRepos_sortsGroupsByLatestDescAndItemsWithinDesc() {
        val groups =
            groupByRepository(
                listOf(
                    item("1", repo = "a/A", updatedAt = "2026-08-01T10:00:00Z"),
                    item("2", repo = "b/B", updatedAt = "2026-08-02T10:00:00Z"),
                    item("3", repo = "a/A", updatedAt = "2026-08-03T10:00:00Z"),
                ),
            )

        // a/A 最新条目（08-03）晚于 b/B（08-02）→ 组间 a/A 在前
        assertEquals(listOf("a/A", "b/B"), groups.map { it.repoFullName })
        assertEquals(listOf("3", "1"), groups[0].items.map { it.id })
    }

    @Test
    fun groupByRepository_invalidOrMissingTimestamp_treatedAsOldest() {
        val groups =
            groupByRepository(
                listOf(
                    item("bad", updatedAt = "not-an-iso"),
                    item("none", updatedAt = null),
                    item("ok", updatedAt = "2026-08-01T10:00:00Z"),
                ),
            )

        assertEquals(1, groups.size)
        assertEquals(listOf("ok", "bad", "none"), groups[0].items.map { it.id })
    }

    @Test
    fun groupByRepository_emptyInput_returnsEmptyList() {
        assertEquals(0, groupByRepository(emptyList()).size)
    }

    @Test
    fun unreadCount_sumsUnreadItemsInGroup() {
        val group =
            NotificationGroup(
                repoFullName = "a/A",
                items = listOf(item("1", unread = true), item("2", unread = false), item("3")),
            )

        assertEquals(2, group.unreadCount)
    }
}
