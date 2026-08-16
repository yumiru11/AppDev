package com.yumiru11.githubapp.core.githubrest.http

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 统一请求头拦截器测试（plan.md §4.3 请求规范）。
 *
 * 断言 Accept / X-GitHub-Api-Version / User-Agent 三个头按 GitHub 规范注入。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class GitHubHeaderInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(GitHubHeaderInterceptor())
                .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun intercept_anyRequest_addsGitHubAcceptHeader() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        client.newCall(Request.Builder().url(server.url("/user")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("application/vnd.github+json", recorded.headers["Accept"])
    }

    @Test
    fun intercept_anyRequest_addsApiVersionHeader() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        client.newCall(Request.Builder().url(server.url("/user")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("2022-11-28", recorded.headers["X-GitHub-Api-Version"])
    }

    @Test
    fun intercept_anyRequest_addsUserAgentHeader() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        client.newCall(Request.Builder().url(server.url("/user")).build()).execute().close()

        val recorded = server.takeRequest()
        // GitHub API 强制要求 User-Agent，缺失会 403
        assertEquals(true, recorded.headers["User-Agent"]?.isNotBlank())
    }

    @Test
    fun intercept_existingCustomAcceptHeader_isPreservedForApiOverride() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        // 显式 Accept（如 ReadmeApi 的 html+json、IssueApi 的 mockingbird preview）必须原样保留，
        // 拦截器只在请求未设置 Accept 时注入默认值（GitHubHeaderInterceptor 注释语义）。
        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/user"))
                    .header("Accept", "application/json")
                    .build(),
            ).execute()
            .close()

        val recorded = server.takeRequest()
        assertEquals(1, recorded.headers.values("Accept").size)
        assertEquals("application/json", recorded.headers["Accept"])
    }

    @Test
    fun intercept_lowercaseAcceptHeader_isPreservedSingleValue() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        // OkHttp 头查找大小写不敏感：小写 accept 同样命中“已设置”分支，不被默认值覆盖
        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/user"))
                    .header("accept", "application/vnd.github.html+json")
                    .build(),
            ).execute()
            .close()

        val recorded = server.takeRequest()
        assertEquals(1, recorded.headers.values("Accept").size)
        assertEquals("application/vnd.github.html+json", recorded.headers["Accept"])
    }

    @Test
    fun intercept_userAgent_isExactAppDevValue() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        client.newCall(Request.Builder().url(server.url("/user")).build()).execute().close()

        val recorded = server.takeRequest()
        // GitHub 强制 User-Agent：断言精确值（同时验证覆盖 OkHttp 默认 UA 而非追加）
        assertEquals("AppDev-GitHub-Client/0.1.0", recorded.headers["User-Agent"])
        assertEquals(1, recorded.headers.values("User-Agent").size)
    }

    @Test
    fun intercept_repeatedCalls_headersAppliedEveryTime() {
        server.enqueue(MockResponse.Builder().body("{}").build())
        server.enqueue(MockResponse.Builder().body("{}").build())

        client.newCall(Request.Builder().url(server.url("/a")).build()).execute().close()
        client.newCall(Request.Builder().url(server.url("/b")).build()).execute().close()

        val first = server.takeRequest()
        val second = server.takeRequest()
        for (request in listOf(first, second)) {
            assertEquals("application/vnd.github+json", request.headers["Accept"])
            assertEquals("2022-11-28", request.headers["X-GitHub-Api-Version"])
            assertEquals("AppDev-GitHub-Client/0.1.0", request.headers["User-Agent"])
        }
    }

    @Test
    fun intercept_postRequest_addsAllHeaders() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/markdown"))
                    .post("".toRequestBody())
                    .build(),
            ).execute()
            .close()

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("application/vnd.github+json", recorded.headers["Accept"])
        assertEquals("2022-11-28", recorded.headers["X-GitHub-Api-Version"])
        assertEquals("AppDev-GitHub-Client/0.1.0", recorded.headers["User-Agent"])
    }

    @Test
    fun intercept_serverReturnsBody_responsePassesThrough() {
        server.enqueue(MockResponse.Builder().body(Buffer().writeUtf8("""{"login":"octocat"}""")).build())

        val response = client.newCall(Request.Builder().url(server.url("/user")).build()).execute()

        assertEquals(200, response.code)
        assertEquals("""{"login":"octocat"}""", response.body.string())
    }
}
