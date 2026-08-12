package com.yumiru11.githubapp.feature.auth

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenEndpointClient
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeResult
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AuthViewModel 接线测试（T4 Wave2）。
 *
 * - 登录按钮 → performAuthorization（内部 buildAuthorizationRequest）+ 回调 PendingIntent 指向 oauth-callback
 * - 游客浏览 → 导航事件（Home，不改变登录态）
 * - PAT 保存 → TokenStorage 落盘（isRestOnly=true）→ authState 变 PAT
 * - 空白 PAT → 忽略（不落盘、不变状态）
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun onSignIn_performsAuthorizationWithOauthCallbackPendingIntent() {
        val sessionManager = mockk<OAuthSessionManager>(relaxed = true)
        every { sessionManager.authState } returns MutableStateFlow(AuthState.Anonymous)
        val viewModel = AuthViewModel(sessionManager, InMemoryTokenStorage(), OAuthConfig(), context)

        viewModel.onSignIn()

        // performAuthorization 内部先 buildAuthorizationRequest 再拉起浏览器（OAuthSessionManager 封装）
        val pendingIntent = slot<PendingIntent>()
        verify { sessionManager.performAuthorization(any(), capture(pendingIntent)) }
        // 回调 PendingIntent 指向 oauth-callback URI（manifest filter 解析到 MainActivity）
        val wrappedIntent = shadowOf(pendingIntent.captured).savedIntent
        assertEquals(Uri.parse(OAuthConfig.REDIRECT_URI), wrappedIntent.data)
        assertEquals(context.packageName, wrappedIntent.`package`)
    }

    @Test
    fun onBrowseAsGuest_emitsHomeNavigationEvent() =
        runTest {
            val sessionManager = mockk<OAuthSessionManager>(relaxed = true)
            every { sessionManager.authState } returns MutableStateFlow(AuthState.Anonymous)
            val viewModel = AuthViewModel(sessionManager, InMemoryTokenStorage(), OAuthConfig(), context)

            viewModel.navigationEvents.test {
                viewModel.onBrowseAsGuest()
                assertEquals(AuthNavigation.Home, awaitItem())
            }
        }

    @Test
    fun onSavePat_savesRestOnlySessionAndRefreshesToPat() =
        runTest {
            val storage = InMemoryTokenStorage()
            val manager = OAuthSessionManager(storage, FakeTokenEndpointClient(), OAuthConfig())
            val viewModel = AuthViewModel(manager, storage, OAuthConfig(), context)

            viewModel.onSavePat("ghp_test_pat")

            assertEquals(AuthState.PAT, manager.authState.value)
            val session = storage.loadSession()
            assertEquals("ghp_test_pat", session.pat)
            assertTrue("PAT 会话必须 isRestOnly（ADR-0003）", session.isRestOnly)
        }

    @Test
    fun onSavePat_blankPat_isIgnored() =
        runTest {
            val storage = InMemoryTokenStorage()
            val manager = OAuthSessionManager(storage, FakeTokenEndpointClient(), OAuthConfig())
            val viewModel = AuthViewModel(manager, storage, OAuthConfig(), context)

            viewModel.onSavePat("   ")

            assertEquals(AuthState.Anonymous, manager.authState.value)
            assertNull(storage.loadSession().pat)
        }
}

/** 假 [TokenEndpointClient]：记录收到的 code，返回固定 token（零网络）。 */
private class FakeTokenEndpointClient : TokenEndpointClient {
    var lastCode: String? = null

    override suspend fun exchangeCode(code: String): TokenExchangeResult {
        lastCode = code
        return TokenExchangeResult(accessToken = "gho_test_access", refreshToken = "ghr_test_refresh")
    }
}
