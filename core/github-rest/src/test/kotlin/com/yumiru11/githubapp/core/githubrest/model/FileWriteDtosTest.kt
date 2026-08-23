package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileWriteRequest]/[FileDeleteRequest]/[ContentWriteResponseDto] 序列化测试（T22）。
 *
 * 覆盖：新建文件省略 null 字段（sha/branch）、更新携带全部字段、删除请求体、
 * 写响应 DTO snake_case 解码。
 */
class FileWriteDtosTest {
    private val json: Json = GitHubRestClient.createJson()

    @Test
    fun fileWriteRequest_update_serializesAllProvidedFields() {
        val encoded =
            json.encodeToString(
                FileWriteRequest.serializer(),
                FileWriteRequest(message = "update", content = "Y29udGVudA==", sha = "blob-old", branch = "main"),
            )

        assertTrue(encoded.contains("\"message\":\"update\""))
        assertTrue(encoded.contains("\"content\":\"Y29udGVudA==\""))
        assertTrue(encoded.contains("\"sha\":\"blob-old\""))
        assertTrue(encoded.contains("\"branch\":\"main\""))
    }

    @Test
    fun fileWriteRequest_createNewFile_omitsNullShaAndBranch() {
        val encoded =
            json.encodeToString(
                FileWriteRequest.serializer(),
                FileWriteRequest(message = "add file", content = "Y29udGVudA=="),
            )

        assertTrue(encoded.contains("\"message\":\"add file\""))
        assertTrue(encoded.contains("\"content\":\"Y29udGVudA==\""))
        assertFalse("新建文件不应序列化 sha", encoded.contains("sha"))
        assertFalse("默认分支不应序列化 branch", encoded.contains("branch"))
    }

    @Test
    fun fileDeleteRequest_serializesMessageShaBranch() {
        val encoded =
            json.encodeToString(
                FileDeleteRequest.serializer(),
                FileDeleteRequest(message = "remove", sha = "blob-old", branch = "main"),
            )

        assertTrue(encoded.contains("\"message\":\"remove\""))
        assertTrue(encoded.contains("\"sha\":\"blob-old\""))
        assertTrue(encoded.contains("\"branch\":\"main\""))
    }

    @Test
    fun contentWriteResponseDto_decodesSnakeCaseResponse() {
        val dto =
            json.decodeFromString<ContentWriteResponseDto>(
                """
                {
                  "content": {"name": "a.txt", "path": "a.txt", "sha": "blob-new", "size": 3},
                  "commit": {"sha": "commit-new", "html_url": "https://github.com/o/r/commit/commit-new"}
                }
                """.trimIndent(),
            )

        assertEquals("blob-new", dto.content?.sha)
        assertEquals("commit-new", dto.commit?.sha)
    }
}
