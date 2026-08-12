package com.yumiru11.githubapp.core.github_auth.auth

import com.yumiru11.githubapp.core.github_auth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.github_auth.token.SessionData
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
 * OAuthSessionManager 状态/登出/回调编排测试（纯 JVM）。
 *
 * HTTP 层走可注入的 [TokenEndpointClient]（MockWebServer3 模拟 GitHub /access_token 端点），
 * 回调 URI 经内部 String 入口（handleCallbackUri）测试——`android.net.Uri` 包装层
 * 见 OAuthSessionManagerRobolectricTest。
 */
class OAuthSessionManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: InMemoryTokenStorage
    private lateinit var manager: OAuthSessionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = InMemoryTokenStorage()
        manager = createManager(storage, server)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun authState_init_emptyStorage_isAnonymous() {
        assertEquals("空存储初始应为 Anonymous", AuthState.Anonymous, manager.authState.value)
    }

    @Test
    fun authState_init_oauthSession_isSignedIn() {
        storage.saveSession(SessionData(accessToken = "gho_1", refreshToken = "ghr_1"))
        val managerWithSession = createManager(storage, server)

        assertEquals(
            "有 access token 应初始为 SignedIn",
            AuthState.SignedIn(SessionData(accessToken = "gho_1", refreshToken = "ghr_1")),
            managerWithSession.authState.value,
        )
    }

    @Test
    fun authState_init_restOnlySession_isPat() {
        storage.saveSession(SessionData(pat = "ghp_dev_pat", isRestOnly = true))
        val managerWithSession = createManager(storage, server)

        assertEquals("isRestOnly 会话应初始为 PAT（ADR-0003）", AuthState.PAT, managerWithSession.authState.value)
    }

    @Test
    fun refreshState_rereadsStorageAndUpdatesState() =
        runTest {
            assertEquals(AuthState.Anonymous, manager.authState.value)

            storage.saveSession(SessionData(accessToken = "gho_1"))
            manager.refreshState()

            assertEquals(AuthState.SignedIn(SessionData(accessToken = "gho_1")), manager.authState.value)
        }

    @Test
    fun signOut_clearsStorageAndFlipsToAnonymous() =
        runTest {
            storage.saveSession(SessionData(accessToken = "gho_1", refreshToken = "ghr_1"))
            manager.refreshState()
            assertEquals(AuthState.SignedIn(SessionData(accessToken = "gho_1", refreshToken = "ghr_1")), manager.authState.value)

            manager.signOut()

            assertEquals(AuthState.Anonymous, manager.authState.value)
            assertEquals("登出应清空 TokenStorage", SessionData(), storage.loadSession())
        }

    @Test
    fun signOut_anonymousSession_isIdempotentNoOp() =
        runTest {
            manager.signOut()

            assertEquals(AuthState.Anonymous, manager.authState.value)
            assertEquals(SessionData(), storage.loadSession())
        }

    @Test
    fun handleCallback_validCallback_exchangesSavesAndSignsIn() =
        runTest {
            server.enqueue(successResponse(access = "gho_access", refresh = "ghr_refresh"))

            val session =
                manager.handleCallbackUri("com.yumiru11.githubapp://oauth-callback?code=abc123&state=xyz")

            assertEquals("gho_access", session.accessToken)
            assertEquals("ghr_refresh", session.refreshToken)
            assertEquals("回调结果应持久化到 TokenStorage", session, storage.loadSession())
            assertEquals("回调后 authState 应为 SignedIn", AuthState.SignedIn(session), manager.authState.value)
        }

    @Test
    fun handleCallback_missingCode_throwsWithoutNetworkOrStorageWrite() =
        runTest {
            val failure =
                runCatching {
                    manager.handleCallbackUri("com.yumiru11.githubapp://oauth-callback?error=access_denied")
                }

            assertTrue(failure.isFailure)
            assertTrue("缺 code 应抛 OAuthCallbackException", failure.exceptionOrNull() is OAuthCallbackException)
            assertEquals("不应发起任何网络请求", 0, server.requestCount)
            assertEquals("失败不应写存储", SessionData(), storage.loadSession())
            assertEquals("失败不应改状态", AuthState.Anonymous, manager.authState.value)
        }

    private fun createManager(
        storage: InMemoryTokenStorage,
        server: MockWebServer,
    ): OAuthSessionManager =
        OAuthSessionManager(
            tokenStorage = storage,
            tokenEndpointClient =
                OkHttpTokenEndpointClient(
                    client = testClient(),
                    config = OAuthConfig(tokenEndpoint = server.url(TOKEN_PATH).toString()),
                ),
            config = OAuthConfig(),
        )

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
