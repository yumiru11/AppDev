package com.yumiru11.githubapp.auth

import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenEndpointClient
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeResult
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.navigation.AppRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录态 → 导航目标映射测试（T4 Wave2 登录态驱动首屏）。
 *
 * 状态流测试：用 InMemoryTokenStorage（fake TokenStorage）+ 假 TokenEndpointClient
 * 驱动真实 [OAuthSessionManager] 状态流转，断言各状态对应的导航目标
 * （Anonymous → 登录页；SignedIn/PAT → 主页）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class AuthNavigationTest {
    @Test
    fun authState_anonymous_mapsToHomeForGuestBrowsing() {
        // 游客直进首页（P0-2 真机走查决策）：登录页仅显式入口可达
        assertEquals(AppRoute.HOME, authStateToDestination(AuthState.Anonymous))
    }

    @Test
    fun authState_signedIn_mapsToHomeDestination() {
        val session = SessionData(accessToken = "gho_test_access")
        assertEquals(AppRoute.HOME, authStateToDestination(AuthState.SignedIn(session)))
    }

    @Test
    fun authState_pat_mapsToHomeDestination() {
        assertEquals(AppRoute.HOME, authStateToDestination(AuthState.PAT))
    }

    @Test
    fun authStateFlow_anonymousToSignedInToPat_staysOnHome() =
        runTest {
            val storage = InMemoryTokenStorage()
            val manager = OAuthSessionManager(storage, NavigationFakeTokenEndpointClient(), OAuthConfig())

            // 初始：Anonymous → 主页（游客直进首页，P0-2）
            assertEquals(AppRoute.HOME, authStateToDestination(manager.authState.value))

            // OAuth 会话落盘 → SignedIn → 主页
            storage.saveSession(SessionData(accessToken = "gho_test_access", refreshToken = "ghr_test_refresh"))
            manager.refreshState()
            assertEquals(AppRoute.HOME, authStateToDestination(manager.authState.value))

            // PAT 落盘（isRestOnly，ADR-0003）→ PAT → 主页
            storage.saveSession(SessionData(pat = "ghp_test_pat", isRestOnly = true))
            manager.refreshState()
            assertEquals(AppRoute.HOME, authStateToDestination(manager.authState.value))
        }

    @Test
    fun shouldNavigateForAuthState_atLoginStaysPut_whenTargetIsLogin() {
        assertFalse(shouldNavigateForAuthState(AppRoute.LOGIN, AppRoute.LOGIN))
    }

    @Test
    fun shouldNavigateForAuthState_atHomeNavigatesToLogin_whenAnonymous() {
        assertTrue(shouldNavigateForAuthState(AppRoute.HOME, AppRoute.LOGIN))
    }

    @Test
    fun shouldNavigateForAuthState_atLoginNavigatesToHome_whenSignedIn() {
        assertTrue(shouldNavigateForAuthState(AppRoute.LOGIN, AppRoute.HOME))
    }

    @Test
    fun shouldNavigateForAuthState_atDeepLinkedPageStaysPut_whenSignedIn() {
        // 已登录 + 深链直达详情页：不打断深链导航（防 popUpTo(0) 清掉深链目标）
        assertFalse(shouldNavigateForAuthState(AppRoute.REPO, AppRoute.HOME))
    }
}

/** 假 [TokenEndpointClient]：记录收到的 code，返回固定 token（零网络）。 */
private class NavigationFakeTokenEndpointClient : TokenEndpointClient {
    var lastCode: String? = null

    override suspend fun exchangeCode(code: String): TokenExchangeResult {
        lastCode = code
        return TokenExchangeResult(accessToken = "gho_test_access", refreshToken = "ghr_test_refresh")
    }
}
