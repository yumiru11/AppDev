package com.yumiru11.githubapp.core.github_auth.auth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * TokenEndpointClient（HTTP 换 token 层）测试（纯 JVM + MockWebServer3，零真实网络）。
 *
 * 覆盖：合法 code → 返回 access/refresh token 且表单字段正确；
 * 端点错误响应 → 抛 TokenExchangeException；响应缺 access_token → 抛异常。
 */
class TokenEndpointClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpTokenEndpointClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            OkHttpTokenEndpointClient(
                client = testClient(),
                config = OAuthConfig(tokenEndpoint = server.url(TOKEN_PATH).toString()),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun exchangeCode_validCode_returnsTokensAndPostsCorrectForm() =
        runTest {
            server.enqueue(successResponse(access = "gho_access", refresh = "ghr_refresh"))

            val result = client.exchangeCode("auth-code-1")

            assertEquals("gho_access", result.accessToken)
            assertEquals("ghr_refresh", result.refreshToken)

            val request = server.takeRequest()
            assertEquals(TOKEN_PATH, request.url.encodedPath)
            assertEquals("POST", request.method)
            val body = request.body?.utf8().orEmpty()
            assertTrue("应提交 grant_type=authorization_code", body.contains("grant_type=authorization_code"))
            assertTrue("应提交 authorization code", body.contains("code=auth-code-1"))
            assertTrue("应提交 client_id", body.contains("client_id=${OAuthConfig.PLACEHOLDER_CLIENT_ID}"))
            assertTrue("应提交 redirect_uri", body.contains("redirect_uri=") && body.contains("oauth-callback"))
            assertTrue("Accept 应为 JSON（要求 GitHub 返回 JSON）", request.headers["Accept"].orEmpty().contains("application/json"))
            // 无多余请求
            assertEquals(1, server.requestCount)
        }

    @Test
    fun exchangeCode_errorResponse_throwsTokenExchangeException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 400 Bad Request")
                    .body("""{"error":"bad_verification_code","error_description":"The code passed is incorrect or expired."}""")
                    .build(),
            )

            val failure = runCatching { client.exchangeCode("bad-code") }

            assertTrue(failure.isFailure)
            assertTrue("端点错误应抛 TokenExchangeException", failure.exceptionOrNull() is TokenExchangeException)
        }

    @Test
    fun exchangeCode_responseWithoutAccessToken_throwsTokenExchangeException() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "application/json")
                    .body("""{"refresh_token":"ghr_only"}""")
                    .build(),
            )

            val failure = runCatching { client.exchangeCode("code") }

            assertTrue(failure.isFailure)
            assertTrue(failure.exceptionOrNull() is TokenExchangeException)
        }

    private fun testClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    private fun successResponse(
        access: String,
        refresh: String?,
    ): MockResponse =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(
                buildString {
                    append("""{"access_token":"$access"""")
                    if (refresh != null) append(""","refresh_token":"$refresh"""")
                    append(""","expires_in":28800,"token_type":"bearer"}""")
                },
            ).build()

    private companion object {
        const val TOKEN_PATH = "/login/oauth/access_token"
    }
}
