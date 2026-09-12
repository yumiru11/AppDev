package com.yumiru11.githubapp.core.githubauth.auth

import com.yumiru11.githubapp.core.githubauth.session.SessionCacheCleaner
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登出必须清空会话级缓存（安全约束：私有响应体不得跨账号残留）。
 *
 * 证明 [OAuthSessionManager.signOut] 逐一调用注入的 [SessionCacheCleaner]，
 * 且个别 cleaner 失败不阻断登出状态翻转。
 */
class OAuthSessionManagerSignOutCacheTest {
    @Test
    fun signOut_invokesAllSessionCacheCleaners() =
        runTest {
            val cleanerA = RecordingCleaner()
            val cleanerB = RecordingCleaner()
            val manager = managerWith(cleanerA, cleanerB)

            manager.signOut()

            assertTrue("cleaner A 应被调用", cleanerA.cleared)
            assertTrue("cleaner B 应被调用", cleanerB.cleared)
        }

    @Test
    fun signOut_cleanerThrows_stillClearsStorageAndFlipsAnonymous() =
        runTest {
            val failing = SessionCacheCleaner { throw IllegalStateException("cache db unavailable") }
            val storage = InMemoryTokenStorage().apply { saveSession(SessionData(accessToken = "gho_1")) }
            val manager = OAuthSessionManager(storage, ThrowingEndpointClient(), OAuthConfig(), setOf(failing))

            manager.signOut()

            assertEquals(AuthState.Anonymous, manager.authState.value)
            assertEquals("登出仍须清空 TokenStorage", SessionData(), storage.loadSession())
        }

    private fun managerWith(vararg cleaners: SessionCacheCleaner): OAuthSessionManager =
        OAuthSessionManager(
            tokenStorage = InMemoryTokenStorage().apply { saveSession(SessionData(accessToken = "gho_1")) },
            tokenEndpointClient = ThrowingEndpointClient(),
            config = OAuthConfig(),
            cacheCleaners = cleaners.toSet(),
        )

    private class RecordingCleaner : SessionCacheCleaner {
        var cleared = false

        override suspend fun clearSessionCache() {
            cleared = true
        }
    }

    private class ThrowingEndpointClient : TokenEndpointClient {
        override suspend fun exchangeCode(code: String): TokenExchangeResult = throw TokenExchangeException("not used in sign-out")
    }
}
