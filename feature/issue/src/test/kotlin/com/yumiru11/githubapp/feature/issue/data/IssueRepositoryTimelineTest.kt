package com.yumiru11.githubapp.feature.issue.data

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IssueRepository 时间线映射测试。
 *
 * 回归覆盖：GitHub timeline 的 cross-referenced 事件 id 为 null（实测 #71），
 * 序列化 + 映射不能抛 UNKNOWN，合成负 id 保证 LazyColumn key 稳定唯一。
 */
class IssueRepositoryTimelineTest {
    @Test
    fun toTimelineItem_crossReferencedNullId_parsesAndUsesSyntheticId() {
        val json =
            """
            {
              "id": null,
              "event": "cross-referenced",
              "created_at": "2026-08-19T12:50:43Z",
              "source": {
                "type": "issue",
                "issue": {
                  "id": 1,
                  "number": 72,
                  "title": "source issue",
                  "state": "open"
                }
              }
            }
            """.trimIndent()

        val dto = GitHubRestClient.createJson().decodeFromString<IssueEventDto>(json)
        val item = dto.toTimelineItem(1)

        assertTrue(item is IssueTimelineItem.Event)
        item as IssueTimelineItem.Event
        assertEquals(IssueTimelineEventType.CROSS_REFERENCED, item.type)
        assertEquals(-2L, item.id)
        assertEquals(72, item.sourceIssue?.number)
    }
}
