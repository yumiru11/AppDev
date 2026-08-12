package com.yumiru11.githubapp.core.github_auth.auth

import android.net.Uri
import com.yumiru11.githubapp.core.github_auth.token.InMemoryTokenStorage
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OAuthSessionManager 的 Android 依赖层测试（Robolectric）。
 *
 * 覆盖 [OAuthSessionManager.buildAuthorizationRequest]（AppAuth 请求构造：端点/scope/
 * PKCE 自动开启）与 public [OAuthSessionManager.handleCallback]（android.net.Uri 入口，
 * 内部 String 编排已由 OAuthSessionManagerTest 纯 JVM 覆盖）。
 * AuthorizationService 真机流程（浏览器拉起）标注「需真机验证」，不在本类测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OAuthSessionManagerRobolectricTest {
    private lateinit var manager: OAuthSessionManager

    @Before
    fun setUp() {
        manager =
            OAuthSessionManager(
                tokenStorage = InMemoryTokenStorage(),
                tokenEndpointClient =
                    OkHttpTokenEndpointClient(
                        client = OkHttpClient(),
                        config = OAuthConfig(),
                    ),
                config = OAuthConfig(),
            )
    }

    @Test
    fun buildAuthorizationRequest_usesConfiguredEndpointsAndScopes() {
        val request = manager.buildAuthorizationRequest()

        assertEquals(OAuthConfig.PLACEHOLDER_CLIENT_ID, request.clientId)
        assertEquals("code", request.responseType)
        assertEquals(OAuthConfig.REDIRECT_URI, request.redirectUri.toString())
        assertEquals(OAuthConfig.DEFAULT_SCOPES, request.scope)
        assertEquals(OAuthConfig.GITHUB_AUTHORIZE_ENDPOINT, request.configuration.authorizationEndpoint.toString())
        assertEquals(OAuthConfig.GITHUB_TOKEN_ENDPOINT, request.configuration.tokenEndpoint.toString())
    }

    @Test
    fun buildAuthorizationRequest_pkceEnabledByDefault() {
        val request = manager.buildAuthorizationRequest()

        assertNotNull("PKCE codeVerifier 应自动生成", request.codeVerifier)
        assertNotNull("PKCE codeVerifierChallenge 应自动生成", request.codeVerifierChallenge)
    }

    @Test
    fun handleCallback_publicUriEntryPoint_exchangesSavesAndSignsIn() =
        runTest {
            val server = MockWebServer()
            server.start()
            try {
                val m =
                    OAuthSessionManager(
                        tokenStorage = InMemoryTokenStorage(),
                        tokenEndpointClient =
                            OkHttpTokenEndpointClient(
                                client = OkHttpClient(),
                                config = OAuthConfig(tokenEndpoint = server.url(TOKEN_PATH).toString()),
                            ),
                        config = OAuthConfig(),
                    )
                server.enqueue(
                    MockResponse
                        .Builder()
                        .addHeader("Content-Type", "application/json")
                        .body("""{"access_token":"gho_access","refresh_token":"ghr_refresh"}""")
                        .build(),
                )

                val session =
                    m.handleCallback(Uri.parse("com.yumiru11.githubapp://oauth-callback?code=abc123&state=xyz"))

                assertEquals("gho_access", session.accessToken)
                assertEquals("ghr_refresh", session.refreshToken)
                assertEquals("回调后 authState 应为 SignedIn", AuthState.SignedIn(session), m.authState.value)
            } finally {
                server.close()
            }
        }

    @After
    fun tearDown() = Unit

    private companion object {
        const val TOKEN_PATH = "/login/oauth/access_token"
    }
}
