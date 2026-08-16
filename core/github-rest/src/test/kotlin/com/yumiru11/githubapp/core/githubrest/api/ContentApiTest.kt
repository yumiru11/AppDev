package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * [ContentApi] 集成测试（MockWebServer 模拟 GitHub Contents API）。
 *
 * 覆盖：base64 内容解码、多段路径保留 "/"、ref 查询参数、
 * 超 1MB 文件 content 为空（GitHub 行为）、非 2xx 抛 HttpException。
 */
class ContentApiTest {
    private lateinit var server: MockWebServer
    private lateinit var contentApi: ContentApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client = GitHubRestClient.createOkHttpClient(GuestTokenProvider(), InMemoryEtagStore(), false),
                json = GitHubRestClient.createJson(),
            )
        contentApi = retrofit.create(ContentApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getFileContent_validResponse_decodesBase64() =
        runTest {
            val source = "fun main() {\n    println(\"hi\")\n}"
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "Main.kt",
                          "path": "src/main/Main.kt",
                          "sha": "abc123",
                          "size": ${source.length},
                          "url": "https://api.github.com/repos/octocat/Hello-World/contents/src/main/Main.kt",
                          "html_url": "https://github.com/octocat/Hello-World/blob/main/src/main/Main.kt",
                          "download_url": "https://raw.githubusercontent.com/octocat/Hello-World/main/src/main/Main.kt",
                          "type": "file",
                          "content": "${Base64.getEncoder().encodeToString(source.toByteArray())}",
                          "encoding": "base64"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = contentApi.getFileContent("octocat", "Hello-World", "src/main/Main.kt", ref = "main")

            assertEquals("Main.kt", dto.name)
            assertEquals("src/main/Main.kt", dto.path)
            assertEquals(source.length.toLong(), dto.size)
            assertEquals(source, dto.decodeContent())

            val request = server.takeRequest()
            // Retrofit @Path 默认编码 "/" 为 %2F；GitHub 对路径参数先 URL 解码，两种形态等价
            assertEquals("/repos/octocat/Hello-World/contents/src%2Fmain%2FMain.kt", request.url.encodedPath)
            assertEquals("main", request.url.queryParameter("ref"))
        }

    @Test
    fun getFileContent_noRef_omitsQueryParameter() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("""{"name": "a.txt", "path": "a.txt", "size": 1}""")
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )

            contentApi.getFileContent("octocat", "Hello-World", "a.txt", ref = null)

            val request = server.takeRequest()
            assertNull("ref 为空时不应带查询参数", request.url.queryParameter("ref"))
        }

    @Test
    fun getFileContent_largeFile_emptyContentField() =
        runTest {
            // GitHub Contents API：>1MB 文件 content 字段为空（须走 Blobs API）——T11 对此给提示
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "big.bin",
                          "path": "big.bin",
                          "sha": "xyz",
                          "size": 2097152,
                          "type": "file",
                          "content": "",
                          "encoding": "base64"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = contentApi.getFileContent("octocat", "Hello-World", "big.bin")

            assertEquals(2_097_152L, dto.size)
            assertEquals("", dto.content)
            assertEquals("", dto.decodeContent())
        }

    @Test
    fun getFileContent_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                contentApi.getFileContent("octocat", "Hello-World", "missing.txt")
                org.junit.Assert.fail("404 应抛 HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(404, e.code())
            }
        }
}
