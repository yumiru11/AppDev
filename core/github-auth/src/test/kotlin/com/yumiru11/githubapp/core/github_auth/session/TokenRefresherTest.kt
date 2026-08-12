package com.yumiru11.githubapp.core.github_auth.session

import com.yumiru11.githubapp.core.github_auth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.github_auth.token.SessionData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * TokenRefresher 刷新测试（JUnit4 + MockWebServer3，零真实网络）。
 *
 * 覆盖：刷新成功更新存储（含 refresh token 轮换）、无 refresh token 不刷新、
 * refresh 过期失败、并发防护（3 并发只刷新一次）、轮换后的二次刷新用新 refresh token。
 */
class TokenRefresherTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: InMemoryTokenStorage
    private lateinit var refresher: TokenRefresher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = InMemoryTokenStorage()
        refresher =
            TokenRefresher(
                tokenStorage = storage,
                config = TokenRefreshConfig(tokenEndpoint = server.url(TOKEN_PATH), clientId = CLIENT_ID),
                client = testClient(),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun refreshIfNeeded_validRefreshToken_updatesStorageAndReturnsTrue() =
        runTest {
            storage.saveSession(SessionData(accessToken = "gho_old", refreshToken = "ghr_old"))
            server.enqueue(refreshSuccessResponse(access = "gho_new", refresh = "ghr_new"))

            val result = refresher.refreshIfNeeded()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow())
            val session = storage.loadSession()
            assertEquals("gho_new", session.accessToken)
            assertEquals("ghr_new", session.refreshToken)

            val request = server.takeRequest()
            assertEquals(TOKEN_PATH, request.url.encodedPath)
            assertEquals("POST", request.method)
            val body = request.body?.utf8().orEmpty()
            assertTrue("grant_type=refresh_token 应写入表单", body.contains("grant_type=refresh_token"))
            assertTrue("旧 refresh_token 应写入表单", body.contains("refresh_token=ghr_old"))
            assertTrue("client_id 应写入表单", body.contains("client_id=$CLIENT_ID"))
            assertTrue("Accept 应为 JSON（要求 GitHub 返回 JSON 而非表单）", request.headers["Accept"].orEmpty().contains("application/json"))
            // 无多余请求
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
        }

    @Test
    fun refreshIfNeeded_noRefreshToken_returnsFalseWithoutNetworkCall() =
        runTest {
            storage.saveSession(SessionData(pat = "ghp_dev_pat", isRestOnly = true))

            val result = refresher.refreshIfNeeded()

            assertTrue(result.isSuccess)
            assertFalse("PAT 模式无 refresh token 应返回 false（不触发刷新）", result.getOrThrow())
            assertEquals("不应发起任何网络请求", 0, server.requestCount)
        }

    @Test
    fun refreshIfNeeded_refreshTokenExpired_returnsFailureAndKeepsSession() =
        runTest {
            storage.saveSession(SessionData(accessToken = "gho_old", refreshToken = "ghr_expired"))
            server.enqueue(
                MockResponse
                    .Builder()
                    .status("HTTP/1.1 401 Unauthorized")
                    .body("""{"error":"bad_refresh_token"}""")
                    .build(),
            )

            val result = refresher.refreshIfNeeded()

            assertTrue(result.isFailure)
            assertEquals("刷新失败不应改动已存会话", "gho_old", storage.loadSession().accessToken)
        }

    @Test
    fun refreshIfNeeded_threeConcurrentCalls_refreshesExactlyOnce() =
        runTest {
            storage.saveSession(SessionData(accessToken = "gho_old", refreshToken = "ghr_old"))
            // token 端点只 enqueue 一次响应：若并发防护失效出现第二次刷新，请求将挂起至超时
            server.enqueue(refreshSuccessResponse(access = "gho_new", refresh = "ghr_new"))

            val results =
                coroutineScope {
                    (1..3).map { async { refresher.refreshIfNeeded() } }.awaitAll()
                }

            results.forEach {
                assertTrue("每个并发调用都应拿到成功结果", it.isSuccess)
                assertTrue(it.getOrThrow())
            }
            assertEquals("3 个并发 401 应只触发一次 token 端点请求", 1, server.requestCount)
            val session = storage.loadSession()
            assertEquals("gho_new", session.accessToken)
            assertEquals("ghr_new", session.refreshToken)
        }

    @Test
    fun refreshIfNeeded_secondRefresh_usesRotatedRefreshToken() =
        runTest {
            storage.saveSession(SessionData(accessToken = "gho_old", refreshToken = "ghr_old"))
            server.enqueue(refreshSuccessResponse(access = "gho_new", refresh = "ghr_new"))

            refresher.refreshIfNeeded()
            // 消费首次刷新请求
            server.takeRequest()

            server.enqueue(refreshSuccessResponse(access = "gho_new2", refresh = "ghr_new2"))
            val result = refresher.refreshIfNeeded()

            assertTrue(result.isSuccess)
            assertEquals("gho_new2", storage.loadSession().accessToken)
            val secondRequest = server.takeRequest()
            val body = secondRequest.body?.utf8().orEmpty()
            assertTrue("轮换后二次刷新应携带新 refresh token", body.contains("refresh_token=ghr_new"))
        }

    private fun testClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    private fun refreshSuccessResponse(
        access: String,
        refresh: String,
    ): MockResponse =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(
                """{"access_token":"$access","refresh_token":"$refresh","expires_in":28800,"token_type":"bearer"}""",
            ).build()

    private companion object {
        const val TOKEN_PATH = "/login/oauth/access_token"
        const val CLIENT_ID = "test-client-id"
    }
}
