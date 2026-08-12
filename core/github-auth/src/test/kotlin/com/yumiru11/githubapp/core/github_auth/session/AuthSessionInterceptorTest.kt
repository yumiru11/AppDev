package com.yumiru11.githubapp.core.github_auth.session

import com.yumiru11.githubapp.core.github_auth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.github_auth.token.SessionData
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * AuthSessionInterceptor 测试（JUnit4 + MockWebServer3，零真实网络）。
 *
 * 覆盖：200 注入 access token / PAT 注入 / 游客不注入 / 401→刷新→重放成功 /
 * 刷新失败返回 401 / PAT 模式 401 不刷新 / 重放后仍 401 不无限重试。
 */
class AuthSessionInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: InMemoryTokenStorage
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = InMemoryTokenStorage()
        client = buildClient()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun intercept_accessTokenPresent_injectsBearerAuthorization() {
        storage.saveSession(SessionData(accessToken = "gho_access_1"))
        server.enqueue(MockResponse.Builder().body("{}").build())

        execute("/user")

        val request = server.takeRequest()
        assertEquals("Bearer gho_access_1", request.headers["Authorization"])
    }

    @Test
    fun intercept_patMode_injectsPatAsBearer() {
        storage.saveSession(SessionData(pat = "ghp_dev_pat", isRestOnly = true))
        server.enqueue(MockResponse.Builder().body("{}").build())

        execute("/user")

        assertEquals("Bearer ghp_dev_pat", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun intercept_guestMode_doesNotInjectAuthorization() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        execute("/repos/octocat/Hello-World")

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun intercept_401WithRefreshToken_refreshesAndRetriesOnceSuccessfully() {
        storage.saveSession(SessionData(accessToken = "gho_stale", refreshToken = "ghr_1"))
        // 第一次 API 请求 → 401；token 端点 → 刷新成功；重放 API 请求 → 200
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 401 Unauthorized").body("{}").build())
        server.enqueue(
            MockResponse
                .Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"access_token":"gho_fresh","refresh_token":"ghr_2","expires_in":28800,"token_type":"bearer"}""")
                .build(),
        )
        server.enqueue(MockResponse.Builder().body("""{"login":"octocat"}""").build())

        val response = execute("/user")

        assertEquals(200, response.code)
        // 请求到达顺序：401 的 API 请求（旧 token）→ token 端点 → 重放请求（新 token）
        assertEquals("Bearer gho_stale", server.takeRequest().headers["Authorization"])
        val tokenRequest = server.takeRequest()
        assertEquals(TOKEN_PATH, tokenRequest.url.encodedPath)
        assertEquals("Bearer gho_fresh", server.takeRequest().headers["Authorization"])
        // 存储已更新
        assertEquals("gho_fresh", storage.loadSession().accessToken)
        assertEquals("ghr_2", storage.loadSession().refreshToken)
    }

    @Test
    fun intercept_401RefreshFails_returnsOriginal401() {
        storage.saveSession(SessionData(accessToken = "gho_stale", refreshToken = "ghr_expired"))
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 401 Unauthorized").body("{}").build())
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 401 Unauthorized").body("""{"error":"bad_refresh_token"}""").build())

        val response = execute("/user")

        assertEquals(401, response.code)
        // 刷新失败后不应再重放 API 请求
        assertEquals(2, server.requestCount)
    }

    @Test
    fun intercept_401InPatMode_doesNotAttemptRefresh() {
        storage.saveSession(SessionData(pat = "ghp_dev_pat", isRestOnly = true))
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 401 Unauthorized").body("{}").build())

        val response = execute("/user")

        assertEquals(401, response.code)
        // 仅一次 API 请求，无 token 端点调用
        assertEquals(1, server.requestCount)
    }

    @Test
    fun intercept_retryStill401_returns401WithoutInfiniteRetry() {
        storage.saveSession(SessionData(accessToken = "gho_stale", refreshToken = "ghr_1"))
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 401 Unauthorized").body("{}").build())
        server.enqueue(
            MockResponse
                .Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"access_token":"gho_fresh","refresh_token":"ghr_2","expires_in":28800,"token_type":"bearer"}""")
                .build(),
        )
        // 重放后仍 401
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 401 Unauthorized").body("{}").build())

        val response = execute("/user")

        assertEquals(401, response.code)
        // 原始请求 + token 端点 + 一次重放 = 3 次，无第二次重放
        assertEquals(3, server.requestCount)
    }

    private fun buildClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                AuthSessionInterceptor(
                    tokenStorage = storage,
                    refresher =
                        TokenRefresher(
                            tokenStorage = storage,
                            config = TokenRefreshConfig(tokenEndpoint = server.url(TOKEN_PATH), clientId = CLIENT_ID),
                            client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build(),
                        ),
                ),
            ).connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    private fun execute(path: String): okhttp3.Response =
        client
            .newCall(Request.Builder().url(server.url(path)).build())
            .execute()

    private companion object {
        const val TOKEN_PATH = "/login/oauth/access_token"
        const val CLIENT_ID = "test-client-id"
    }
}
