package com.yumiru11.githubapp.core.github_auth.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 内存 TokenStorage 存储语义测试（ADR-0002；纯 JVM，无需 Android 环境）。
 *
 * 覆盖：默认空会话 / 读写往返 / 整体覆盖 / 降级标记持久化 / 清除。
 * 语义约束：loadSession 永不抛异常（未写入时返回全空 SessionData）。
 */
class InMemoryTokenStorageTest {

    private lateinit var storage: InMemoryTokenStorage

    @Before
    fun setUp() {
        storage = InMemoryTokenStorage()
    }

    @Test
    fun loadSession_neverWritten_returnsEmptySession() {
        val session = storage.loadSession()

        assertNull(session.accessToken)
        assertNull(session.refreshToken)
        assertNull(session.pat)
        assertFalse(session.isRestOnly)
    }

    @Test
    fun saveSession_thenLoadSession_returnsSameValues() {
        storage.saveSession(
            SessionData(
                accessToken = "gho_access_123",
                refreshToken = "ghr_refresh_456",
                pat = "ghp_pat_789",
                isRestOnly = false,
            ),
        )

        val read = storage.loadSession()

        assertEquals("gho_access_123", read.accessToken)
        assertEquals("ghr_refresh_456", read.refreshToken)
        assertEquals("ghp_pat_789", read.pat)
        assertFalse(read.isRestOnly)
    }

    @Test
    fun saveSession_secondWrite_overwritesPreviousSession() {
        storage.saveSession(SessionData(accessToken = "first", refreshToken = "old-refresh"))

        storage.saveSession(SessionData(accessToken = "second", pat = "ghp_dev_pat"))

        val read = storage.loadSession()
        assertEquals("second", read.accessToken)
        // 第二次整体覆盖：未提供的字段回到空值，不残留首次会话数据
        assertNull(read.refreshToken)
        assertEquals("ghp_dev_pat", read.pat)
        assertFalse(read.isRestOnly)
    }

    @Test
    fun saveSession_patMode_setsRestOnlyFlag() {
        storage.saveSession(SessionData(pat = "ghp_dev_pat", isRestOnly = true))

        val read = storage.loadSession()
        assertNull(read.accessToken)
        assertNull(read.refreshToken)
        assertEquals("ghp_dev_pat", read.pat)
        assertTrue(read.isRestOnly)
    }

    @Test
    fun isRestOnly_true_survivesSaveAndLoadRoundTrip() {
        storage.saveSession(SessionData(accessToken = "gho_1", isRestOnly = true))

        assertTrue(storage.loadSession().isRestOnly)
    }

    @Test
    fun clear_afterSave_returnsEmptySession() {
        storage.saveSession(
            SessionData(accessToken = "gho_1", refreshToken = "ghr_2", isRestOnly = true),
        )

        storage.clear()

        val read = storage.loadSession()
        assertNull(read.accessToken)
        assertNull(read.refreshToken)
        assertNull(read.pat)
        assertFalse(read.isRestOnly)
    }

    @Test
    fun clear_whenAlreadyEmpty_isNoOp() {
        storage.clear()

        assertNull(storage.loadSession().accessToken)
        assertNull(storage.loadSession().refreshToken)
        assertNull(storage.loadSession().pat)
        assertFalse(storage.loadSession().isRestOnly)
    }
}
