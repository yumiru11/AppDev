package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CreateReviewCommentRequest]/[UpdateReviewCommentRequest] 序列化测试（T16）。
 *
 * 覆盖：新增评论全字段 snake_case 序列化、回复模式省略其余 null 字段（encodeDefaults=false）、
 * 编辑请求体。
 */
class ReviewCommentWriteDtosTest {
    private val json: Json = GitHubRestClient.createJson()

    @Test
    fun createReviewComment_fullFields_serializesSnakeCase() {
        val encoded =
            json.encodeToString(
                CreateReviewCommentRequest(
                    body = "Nice",
                    commitId = "abc123",
                    path = "README.md",
                    line = 10,
                    side = "RIGHT",
                ),
            )

        assertTrue(encoded.contains("\"body\":\"Nice\""))
        assertTrue(encoded.contains("\"commit_id\":\"abc123\""))
        assertTrue(encoded.contains("\"path\":\"README.md\""))
        assertTrue(encoded.contains("\"line\":10"))
        assertTrue(encoded.contains("\"side\":\"RIGHT\""))
        assertFalse(encoded.contains("in_reply_to_id"))
    }

    @Test
    fun createReviewComment_replyOnly_omitsOtherNullFields() {
        val encoded =
            json.encodeToString(
                CreateReviewCommentRequest(body = "Replied", inReplyToId = 7L),
            )

        assertEquals("{\"body\":\"Replied\",\"in_reply_to_id\":7}", encoded)
        assertFalse(encoded.contains("commit_id"))
        assertFalse(encoded.contains("\"path\""))
        assertFalse(encoded.contains("\"line\""))
        assertFalse(encoded.contains("\"side\""))
    }

    @Test
    fun updateReviewComment_serializesBody() {
        val encoded = json.encodeToString(UpdateReviewCommentRequest(body = "Edited"))

        assertEquals("{\"body\":\"Edited\"}", encoded)
    }
}
