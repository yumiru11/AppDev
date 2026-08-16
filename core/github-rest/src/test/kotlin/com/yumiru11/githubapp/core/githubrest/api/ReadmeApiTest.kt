package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.GitHubHeaderInterceptor
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import com.yumiru11.githubapp.core.githubrest.model.MarkdownRenderRequest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ReadmeApi] 集成测试（MockWebServer 模拟 GitHub API）。
 *
 * 覆盖：HTML Accept 模式返回服务端渲染 HTML、JSON 模式返回元数据 + base64 内容解码、
 * POST /markdown GFM 渲染、Accept 头与 query 参数正确拼装、非 2xx 抛 HttpException。
 */
class ReadmeApiTest {
    private lateinit var server: MockWebServer
    private lateinit var readmeApi: ReadmeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit =
            GitHubRestClient.createRetrofit(
                baseUrl = server.url("/"),
                client =
                    GitHubRestClient
                        .createOkHttpClient(
                            tokenProvider = GuestTokenProvider(),
                            etagStore = InMemoryEtagStore(),
                            debugLogging = false,
                        ).newBuilder()
                        .addInterceptor(GitHubHeaderInterceptor())
                        .build(),
                json = GitHubRestClient.createJson(),
            )
        readmeApi = retrofit.create(ReadmeApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getReadmeHtml_validResponse_returnsRenderedHtml() =
        runTest {
            val html = "<article><h1>Hello-World</h1><p>This is a README.</p></article>"
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(html)
                    .addHeader("Content-Type", "application/vnd.github.html+json; charset=utf-8")
                    .build(),
            )

            val result = readmeApi.getReadmeHtml("octocat", "Hello-World")

            assertEquals(html, result.string())
            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/readme", request.url.encodedPath)
            assertEquals("application/vnd.github.html+json", request.headers["Accept"])
        }

    @Test
    fun getReadmeHtml_withRef_passesQueryParameter() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body("<p>readme</p>")
                    .addHeader("Content-Type", "application/vnd.github.html+json; charset=utf-8")
                    .build(),
            )

            readmeApi.getReadmeHtml("octocat", "Hello-World", ref = "develop")

            val request = server.takeRequest()
            assertEquals("develop", request.url.queryParameter("ref"))
        }

    @Test
    fun getReadmeMeta_validResponse_decodesBase64Content() =
        runTest {
            // "Hello, World!" base64 编码后 → SGVsbG8sIFdvcmxkIQ==
            val base64Content = "SGVsbG8sIFdvcmxkIQ=="
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc123",
                          "size": 14,
                          "url": "https://api.github.com/repos/octocat/Hello-World/contents/README.md",
                          "html_url": "https://github.com/octocat/Hello-World/blob/main/README.md",
                          "download_url": "https://raw.githubusercontent.com/octocat/Hello-World/main/README.md",
                          "type": "file",
                          "content": "$base64Content",
                          "encoding": "base64"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = readmeApi.getReadmeMeta("octocat", "Hello-World")

            assertEquals("README.md", dto.name)
            assertEquals("abc123", dto.sha)
            assertEquals("https://github.com/octocat/Hello-World/blob/main/README.md", dto.htmlUrl)
            assertEquals("base64", dto.encoding)
            assertEquals("Hello, World!", dto.decodeContent())

            val request = server.takeRequest()
            assertEquals("/repos/octocat/Hello-World/readme", request.url.encodedPath)
            assertEquals("application/json", request.headers["Accept"])
        }

    @Test
    fun getReadmeMeta_404Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 404 Not Found")
                    .body("""{"message":"Not Found"}""")
                    .build(),
            )

            try {
                readmeApi.getReadmeMeta("octocat", "Missing-Repo")
                org.junit.Assert.fail("404 应抛 HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(404, e.code())
            }
        }

    @Test
    fun getReadmeMeta_emptyRepo_returns409AndThrowsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 409 Conflict")
                    .body("""{"message":"This repository is empty."}""")
                    .build(),
            )

            try {
                readmeApi.getReadmeMeta("octocat", "Empty-Repo")
                org.junit.Assert.fail("空仓库应抛 HttpException 409")
            } catch (e: retrofit2.HttpException) {
                assertEquals(409, e.code())
            }
        }

    @Test
    fun renderMarkdown_validRequest_returnsHtmlAndPostsBody() =
        runTest {
            val renderedHtml = "<h1>Hello</h1>"
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(renderedHtml)
                    .addHeader("Content-Type", "text/html; charset=utf-8")
                    .build(),
            )

            val request =
                MarkdownRenderRequest(
                    text = "# Hello",
                    mode = "gfm",
                    context = "octocat/Hello-World",
                )
            val result = readmeApi.renderMarkdown(request)

            assertEquals(renderedHtml, result.string())

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/markdown", recorded.url.encodedPath)
            assertEquals("application/json; charset=utf-8", recorded.headers["Content-Type"])
            val body = recorded.body?.utf8().orEmpty()
            assertTrue("请求体含 text 字段", body.contains("\"text\":\"# Hello\""))
            // mode 有默认值 "gfm"，kotlinx.serialization 默认排除默认值字段
            assertTrue("请求体含 context", body.contains("\"context\":\"octocat/Hello-World\""))
        }

    @Test
    fun getReadmeMeta_nullContent_decodeReturnsNull() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc123"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = readmeApi.getReadmeMeta("octocat", "Hello-World")

            assertEquals("README.md", dto.name)
            assertEquals("content 缺失时解码应为 null", null, dto.decodeContent())
        }

    @Test
    fun getReadmeMeta_nonBase64Encoding_returnsContentRaw() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc123",
                          "content": "plain text content",
                          "encoding": "utf-8"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = readmeApi.getReadmeMeta("octocat", "Hello-World")

            assertEquals("非 base64 编码应原样返回", "plain text content", dto.decodeContent())
        }

    @Test
    fun getReadmeMeta_invalidBase64_returnsNull() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc123",
                          "content": "!!!not-base64!!!",
                          "encoding": "base64"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = readmeApi.getReadmeMeta("octocat", "Hello-World")

            assertEquals("非法 base64 应容错返回 null", null, dto.decodeContent())
        }

    @Test
    fun getReadmeMeta_contentWithNewlines_decodesCorrectly() =
        runTest {
            // "Hello,\nWorld!" base64：SGVsbG8sCldvcmxkIQ==（GitHub 返回的 content 常带 \n 分段）
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc123",
                          "content": "SGVsbG8sCldvcmxkIQ==",
                          "encoding": "base64"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            val dto = readmeApi.getReadmeMeta("octocat", "Hello-World")

            assertEquals("Hello,\nWorld!", dto.decodeContent())
        }

    @Test
    fun getReadmeMeta_withRef_passesQueryParameter() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc123"
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )

            readmeApi.getReadmeMeta("octocat", "Hello-World", ref = "develop")

            val request = server.takeRequest()
            assertEquals("develop", request.url.queryParameter("ref"))
            assertEquals("application/json", request.headers["Accept"])
        }

    @Test
    fun renderMarkdown_500Response_throwsHttpException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 500 Internal Server Error")
                    .body("""{"message":"Server Error"}""")
                    .build(),
            )

            try {
                readmeApi.renderMarkdown(MarkdownRenderRequest(text = "# Hi", mode = "gfm", context = null))
                org.junit.Assert.fail("500 应抛 HttpException")
            } catch (e: retrofit2.HttpException) {
                assertEquals(500, e.code())
            }
        }
}
