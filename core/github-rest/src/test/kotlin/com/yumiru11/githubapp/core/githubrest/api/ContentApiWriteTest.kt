package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.githubrest.model.FileDeleteRequest
import com.yumiru11.githubapp.core.githubrest.model.FileWriteRequest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.util.Base64

/**
 * [ContentApi] 写操作集成测试（T22，MockWebServer 模拟 GitHub Contents API）。
 *
 * 覆盖：updateFileContent 的 PUT 请求体（message/content base64/sha/branch）、
 * 新建文件省略 sha、deleteFile 的 DELETE body 参数、写响应 DTO 解析、409/404 抛 HttpException。
 */
class ContentApiWriteTest {
    private lateinit var server: MockWebServer
    private lateinit var contentApi: ContentApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client =
                    GitHubRestClient.createOkHttpClient(
                        tokenProvider = GuestTokenProvider(),
                        etagStore = InMemoryEtagStore(),
                        debugLogging = false,
                    ),
                json = GitHubRestClient.createJson(),
            )
        contentApi = retrofit.create(ContentApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun updateFileContent_update_sendsPutWithBase64AndParsesResponse() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "content": {"name": "probe.txt", "path": "probe.txt", "sha": "blob-new", "size": 12},
                          "commit": {"sha": "commit-new", "html_url": "https://github.com/o/r/commit/commit-new"}
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val text = "line1\nline2\n"
            val response =
                contentApi.updateFileContent(
                    "octocat",
                    "Hello-World",
                    "probe.txt",
                    FileWriteRequest(
                        message = "update probe",
                        content = Base64.getEncoder().encodeToString(text.toByteArray()),
                        sha = "blob-old",
                        branch = "main",
                    ),
                )

            assertEquals("blob-new", response.content?.sha)
            assertEquals("commit-new", response.commit?.sha)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/repos/octocat/Hello-World/contents/probe.txt", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertTrue("请求体应含 message", body.contains("\"message\":\"update probe\""))
            assertTrue(
                "请求体应含 base64 content",
                body.contains("\"content\":\"" + Base64.getEncoder().encodeToString(text.toByteArray()) + "\""),
            )
            assertTrue("更新应携带 sha", body.contains("\"sha\":\"blob-old\""))
            assertTrue("请求体应含 branch", body.contains("\"branch\":\"main\""))
        }

    @Test
    fun updateFileContent_createNewFile_omitsShaAndBranchFromBody() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"content": {"sha": "blob-new"}, "commit": {"sha": "c1"}}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            contentApi.updateFileContent(
                "octocat",
                "Hello-World",
                "new.txt",
                FileWriteRequest(message = "add new.txt", content = "bmV3IGRvYw=="),
            )

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue("新建文件应携带 content", body.contains("\"content\":\"bmV3IGRvYw==\""))
            assertTrue("新建文件 sha 为 null 不应序列化", !body.contains("\"sha\""))
            assertTrue("分支为 null 不应序列化", !body.contains("\"branch\""))
        }

    @Test
    fun updateFileContent_conflict409_throwsHttpExceptionWithBody() =
        runTest {
            // GitHub 409 响应：message 内嵌当前文件 sha（2026-08-22 实测格式）
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 409 Conflict")
                    .body(
                        """{"message": "probe.txt does not match c0d0fb45c382919737f8d0c20aaf57cf89b74af8", "status": "409"}""",
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            try {
                contentApi.updateFileContent(
                    "octocat",
                    "Hello-World",
                    "probe.txt",
                    FileWriteRequest(message = "stale", content = "c3RhbGU=", sha = "blob-old", branch = "main"),
                )
                fail("409 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(409, e.code())
                val errorBody =
                    e
                        .response()
                        ?.errorBody()
                        ?.string()
                        .orEmpty()
                assertTrue("409 body 应含最新 sha", errorBody.contains("c0d0fb45c382919737f8d0c20aaf57cf89b74af8"))
            }
        }

    @Test
    fun deleteFile_validRequest_sendsDeleteWithBodyParamsAndParsesResponse() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"content": null, "commit": {"sha": "commit-deleted"}}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            val response =
                contentApi.deleteFile(
                    "octocat",
                    "Hello-World",
                    "probe.txt",
                    FileDeleteRequest(message = "remove probe", sha = "blob-old", branch = "main"),
                )

            assertEquals("commit-deleted", response.commit?.sha)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/repos/octocat/Hello-World/contents/probe.txt", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertTrue("删除请求体应含 message", body.contains("\"message\":\"remove probe\""))
            assertTrue("删除请求体应含 sha", body.contains("\"sha\":\"blob-old\""))
            assertTrue("删除请求体应含 branch", body.contains("\"branch\":\"main\""))
        }

    @Test
    fun deleteFile_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                contentApi.deleteFile(
                    "octocat",
                    "Hello-World",
                    "missing.txt",
                    FileDeleteRequest(message = "remove", sha = "blob-old"),
                )
                fail("404 应抛 HttpException")
            } catch (e: HttpException) {
                assertEquals(404, e.code())
            }
        }
}
