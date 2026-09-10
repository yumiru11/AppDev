package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [UpdateIssueRequest] / [UpdatePullRequestRequest] 自定义序列化器测试（#163 L02/L03）。
 *
 * 核心契约：**只把变更字段进 PATCH**——null 字段一律不出现在请求体
 * （GitHub 对 labels/assignees/milestone 的显式 null 语义是「清空」，
 * 误携带未变更字段会清掉用户数据）；里程碑清空是唯一需要显式 null 的场景。
 */
class UpdateRequestSerializersTest {
    private val json: Json = GitHubRestClient.createJson()

    @Test
    fun updateIssueRequest_labelsOnly_omitsOtherFields() {
        val encoded = json.encodeToString(UpdateIssueRequest(labels = listOf("bug", "ui")))

        assertEquals("{\"labels\":[\"bug\",\"ui\"]}", encoded)
        assertFalse(encoded.contains("assignees"))
        assertFalse(encoded.contains("milestone"))
    }

    @Test
    fun updateIssueRequest_assigneesAndMilestone_serializesBoth() {
        val encoded =
            json.encodeToString(
                UpdateIssueRequest(assignees = listOf("octocat"), milestone = 7L),
            )

        assertEquals("{\"assignees\":[\"octocat\"],\"milestone\":7}", encoded)
        assertFalse(encoded.contains("labels"))
    }

    @Test
    fun updateIssueRequest_emptyAssignees_serializesEmptyArrayToClear() {
        val encoded = json.encodeToString(UpdateIssueRequest(assignees = emptyList()))

        assertEquals("{\"assignees\":[]}", encoded)
    }

    @Test
    fun updateIssueRequest_clearMilestone_serializesExplicitNull() {
        val encoded = json.encodeToString(UpdateIssueRequest(clearMilestone = true))

        assertEquals("{\"milestone\":null}", encoded)
    }

    @Test
    fun updateIssueRequest_noChanges_serializesEmptyObject() {
        val encoded = json.encodeToString(UpdateIssueRequest())

        assertEquals("{}", encoded)
    }

    @Test
    fun updatePullRequestRequest_stateOnly_omitsTitleAndBody() {
        val encoded = json.encodeToString(UpdatePullRequestRequest(state = "closed"))

        assertEquals("{\"state\":\"closed\"}", encoded)
    }

    @Test
    fun updatePullRequestRequest_titleAndEmptyBody_keepsEmptyStringForClearing() {
        val encoded = json.encodeToString(UpdatePullRequestRequest(title = "New title", body = ""))

        assertEquals("{\"title\":\"New title\",\"body\":\"\"}", encoded)
    }

    @Test
    fun updatePullRequestRequest_noChanges_serializesEmptyObject() {
        val encoded = json.encodeToString(UpdatePullRequestRequest())

        assertEquals("{}", encoded)
    }
}
