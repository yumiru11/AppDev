package com.yumiru11.githubapp.auth

import android.net.Uri
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthCallbackException
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenEndpointClient
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeResult
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OAuth 回调处理测试（T4 Wave2）：伪回调 URI → handleCallback 路径。
 *
 * 用假 [TokenEndpointClient]（零网络）验证：code 提取 → token 交换 → 会话持久化 → SignedIn；
 * 无 code 的错误回调（用户取消）→ 抛 [OAuthCallbackException] 且状态保持 Anonymous。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OAuthCallbackHandlingTest {
    @Test
    fun handleCallback_oauthCallbackUri_exchangesCodeAndSignsIn() =
        runTest {
            val storage = InMemoryTokenStorage()
            val client = CallbackFakeTokenEndpointClient()
            val manager = OAuthSessionManager(storage, client, OAuthConfig())

            val session =
                manager.handleCallback(Uri.parse("com.yumiru11.githubapp://oauth-callback?code=test-code-42"))

            assertEquals("test-code-42", client.lastCode)
            assertEquals("gho_test_access", session.accessToken)
            assertEquals(AuthState.SignedIn(session), manager.authState.value)
            assertEquals(session, storage.loadSession())
        }

    @Test
    fun handleCallback_uriWithoutCode_throwsAndStaysAnonymous() =
        runTest {
            val storage = InMemoryTokenStorage()
            val manager = OAuthSessionManager(storage, CallbackFakeTokenEndpointClient(), OAuthConfig())

            var thrown: OAuthCallbackException? = null
            try {
                manager.handleCallback(Uri.parse("com.yumiru11.githubapp://oauth-callback?error=access_denied"))
            } catch (e: OAuthCallbackException) {
                thrown = e
            }
            assertNotNull("无 code 的错误回调应抛 OAuthCallbackException", thrown)
            assertEquals(AuthState.Anonymous, manager.authState.value)
        }
}

/** 假 [TokenEndpointClient]：记录收到的 code，返回固定 token（零网络）。 */
private class CallbackFakeTokenEndpointClient : TokenEndpointClient {
    var lastCode: String? = null

    override suspend fun exchangeCode(code: String): TokenExchangeResult {
        lastCode = code
        return TokenExchangeResult(accessToken = "gho_test_access", refreshToken = "ghr_test_refresh")
    }
}
