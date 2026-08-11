package com.yumiru11.githubapp.core.githubrest.http

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
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
    fun intercept_existingCustomAcceptHeader_isReplacedNotDuplicated() {
        server.enqueue(MockResponse.Builder().body("{}").build())

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
        assertEquals("application/vnd.github+json", recorded.headers["Accept"])
    }

    @Test
    fun intercept_serverReturnsBody_responsePassesThrough() {
        server.enqueue(MockResponse.Builder().body(Buffer().writeUtf8("""{"login":"octocat"}""")).build())

        val response = client.newCall(Request.Builder().url(server.url("/user")).build()).execute()

        assertEquals(200, response.code)
        assertEquals("""{"login":"octocat"}""", response.body.string())
    }
}
