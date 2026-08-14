package com.yumiru11.githubapp.di

import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [SessionTokenProvider] 三态读取测试（P0-7 修复验证）。
 *
 * 读取顺序与 AuthState 推导一致：PAT（isRestOnly）→ pat；OAuth → accessToken；
 * 无会话 → null（游客匿名，不注入请求头）。
 */
class SessionTokenProviderTest {
    @Test
    fun token_withPatSession_returnsPat() {
        val storage = InMemoryTokenStorage()
        storage.saveSession(SessionData(pat = "ghp_test_pat", isRestOnly = true))

        assertEquals("ghp_test_pat", SessionTokenProvider(storage).token())
    }

    @Test
    fun token_withOAuthSession_returnsAccessToken() {
        val storage = InMemoryTokenStorage()
        storage.saveSession(SessionData(accessToken = "gho_test_access"))

        assertEquals("gho_test_access", SessionTokenProvider(storage).token())
    }

    @Test
    fun token_withNoSession_returnsNull() {
        assertEquals(null, SessionTokenProvider(InMemoryTokenStorage()).token())
    }
}
