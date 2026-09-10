package com.yumiru11.githubapp.feature.notifications.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [groupByRepository] 纯函数单测：分组、组内/组间时间排序（两种 [NotificationSortOrder]）、
 * 非法时间戳兜底、未读计数。
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
    fun groupByRepository_oldestFirst_sortsItemsAscendingAndGroupsByOldestAscending() {
        val groups =
            groupByRepository(
                listOf(
                    item("1", repo = "a/A", updatedAt = "2026-08-03T10:00:00Z"),
                    item("2", repo = "b/B", updatedAt = "2026-08-01T10:00:00Z"),
                    item("3", repo = "a/A", updatedAt = "2026-08-02T10:00:00Z"),
                ),
                NotificationSortOrder.OLDEST_FIRST,
            )

        // 组间：b/B 最旧一条（08-01）早于 a/A（08-02）→ b/B 在前（与倒序恰为镜像）
        assertEquals(listOf("b/B", "a/A"), groups.map { it.repoFullName })
        // 组内正序：a/A 的 08-02（id=3）在 08-03（id=1）之前
        assertEquals(listOf("3", "1"), groups.first { it.repoFullName == "a/A" }.items.map { it.id })
    }

    @Test
    fun groupByRepository_defaultOrder_isNewestFirst() {
        val input =
            listOf(
                item("old", updatedAt = "2026-08-01T10:00:00Z"),
                item("new", updatedAt = "2026-08-05T10:00:00Z"),
            )

        val default = groupByRepository(input)
        val explicit = groupByRepository(input, NotificationSortOrder.NEWEST_FIRST)

        assertEquals(explicit[0].items.map { it.id }, default[0].items.map { it.id })
        assertEquals(listOf("new", "old"), default[0].items.map { it.id })
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
