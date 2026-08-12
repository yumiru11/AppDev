package com.yumiru11.githubapp.core.githubauth.session

import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAT 降级逻辑测试（ADR-0003）：fine-grained PAT 不支持 GraphQL → REST-only。
 *
 * 覆盖：loginWithPat 持久化（pat + isRestOnly=true）、整体覆盖旧会话、isRestOnly 判定。
 */
class DowngradeTest {
    @Test
    fun loginWithPat_validPat_persistsRestOnlySession() =
        runTest {
            val storage = InMemoryTokenStorage()

            val session = loginWithPat(storage, "ghp_dev_pat_123")

            assertEquals("ghp_dev_pat_123", session.pat)
            assertTrue(session.isRestOnly)
            assertNull(session.accessToken)
            assertNull(session.refreshToken)

            val persisted = storage.loadSession()
            assertEquals("ghp_dev_pat_123", persisted.pat)
            assertTrue("PAT 模式必须持久化 REST-only 标记", persisted.isRestOnly)
            assertNull(persisted.accessToken)
            assertNull(persisted.refreshToken)
        }

    @Test
    fun loginWithPat_afterOAuthSession_overwritesAllCredentials() =
        runTest {
            val storage = InMemoryTokenStorage()
            storage.saveSession(SessionData(accessToken = "gho_old", refreshToken = "ghr_old"))

            loginWithPat(storage, "ghp_dev_pat_456")

            val persisted = storage.loadSession()
            assertEquals("ghp_dev_pat_456", persisted.pat)
            assertTrue(persisted.isRestOnly)
            assertNull("OAuth 凭据应被整体覆盖清除", persisted.accessToken)
            assertNull(persisted.refreshToken)
        }

    @Test
    fun isRestOnly_patSession_returnsTrue() {
        assertTrue(isRestOnly(SessionData(pat = "ghp_x", isRestOnly = true)))
    }

    @Test
    fun isRestOnly_oauthSession_returnsFalse() {
        assertFalse(isRestOnly(SessionData(accessToken = "gho_x")))
    }
}
