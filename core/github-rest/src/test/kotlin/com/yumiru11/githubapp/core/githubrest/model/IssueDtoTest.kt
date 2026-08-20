package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [IssueDto] 及其关联 DTO 的序列化往返与 data class 契约测试（T15 覆盖率修复）。
 *
 * 纯单测：kotlinx-serialization JSON 往返，无需 MockWebServer。
 * 重点覆盖 T15 新增的 [IssueDto.repositoryUrl] 字段，以及既有的 timeline 关联链路
 *（[IssueEventDto] → [CrossReferenceSourceDto]），并补足序列化方向 /
 * equals-hashCode-toString-copy 契约。
 *
 * UserDto / LabelDto / MilestoneDto / ReactionsDto 在 main 已存在且已被 IssueApiTest 覆盖，
 * 此处仅作为嵌套对象随 IssueDto 往返，不再单独补测。
 */
class IssueDtoTest {
    private val json: Json = GitHubRestClient.createJson()

    @Test
    fun issueDto_allFields_roundTripsAndExercisesContract() {
        val user = UserDto(login = "octocat", id = 1)
        val milestone = MilestoneDto(title = "v1", state = "open", description = "d")
        val label = LabelDto(name = "bug", color = "d73a4a")
        val issue =
            IssueDto(
                id = 5L,
                number = 9,
                title = "Bug",
                state = "open",
                body = "body",
                user = user,
                labels = listOf(label),
                assignees = listOf(user),
                milestone = milestone,
                reactions = ReactionsDto(totalCount = 1),
                comments = 4,
                createdAt = "2026-08-01T00:00:00Z",
                updatedAt = "2026-08-02T00:00:00Z",
                closedAt = null,
                htmlUrl = "https://x/issues/9",
                pullRequest = null,
                repositoryUrl = "https://api.github.com/repos/octocat/Hello",
            )

        val encoded = json.encodeToString(IssueDto.serializer(), issue)
        val decoded = json.decodeFromString(IssueDto.serializer(), encoded)
        assertEquals(issue, decoded)
        assertEquals(issue.hashCode(), decoded.hashCode())
        assertEquals(issue.toString(), decoded.toString())
        assertEquals("https://api.github.com/repos/octocat/Hello", decoded.repositoryUrl)
        assertFalse("equals 应拒绝非同类对象", issue.equals("not an issue"))
        assertFalse("equals 应区分差异字段", issue.equals(issue.copy(number = 10)))

        // 缺省参数构造路径
        val minimal = IssueDto(id = 1L, number = 2, title = "t", state = "open")
        val minEncoded = json.encodeToString(IssueDto.serializer(), minimal)
        val minDecoded = json.decodeFromString(IssueDto.serializer(), minEncoded)
        assertEquals(minimal, minDecoded)
    }

    @Test
    fun issueEventDto_crossReference_roundTrips() {
        val user = UserDto(login = "octocat", id = 1)
        val linkedIssue =
            IssueDto(
                id = 5L,
                number = 9,
                title = "Bug",
                state = "open",
                repositoryUrl = "https://api.github.com/repos/octocat/Hello",
            )
        val event =
            IssueEventDto(
                id = 1L,
                event = "cross-referenced",
                actor = user,
                source = CrossReferenceSourceDto(issue = linkedIssue),
            )

        val encoded = json.encodeToString(IssueEventDto.serializer(), event)
        val decoded = json.decodeFromString(IssueEventDto.serializer(), encoded)
        assertEquals(event, decoded)
        assertEquals(event.hashCode(), decoded.hashCode())
        assertEquals(event.toString(), decoded.toString())
        assertFalse(event.equals("not an event"))
    }
}
