package com.yumiru11.githubapp.core.githubdata.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenEndpointClient
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeException
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeResult
import com.yumiru11.githubapp.core.githubauth.token.InMemoryTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubrest.http.EtagEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 登出清空的端到端证明：真实 [OAuthSessionManager] 持有真实 [RoomEtagStore] 作为 cleaner，
 * signOut 后持久化 ETag 表必须为空（跨进程残留的私有正文一并抹除）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EtagSignOutIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: EtagCacheDao
    private lateinit var store: RoomEtagStore

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).build()
        dao = db.etagCacheDao()
        store = RoomEtagStore(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun signOut_clearsPersistedEtagCacheAndFlipsAnonymous() =
        runBlocking {
            val url = "https://api.github.com/repos/octocat/Private-Repo"
            store.put("account-a", "GET", url, EtagEntry("W/\"e\"", "application/json", "{\"private\":true}"))
            val storage = InMemoryTokenStorage().apply { saveSession(SessionData(accessToken = "gho_account_a")) }
            val manager = OAuthSessionManager(storage, UnusedEndpointClient(), OAuthConfig(), setOf(store))

            manager.signOut()

            assertNull("登出后不得再命中任何缓存条目", store.get("account-a", "GET", url))
            assertEquals("登出后持久化 etag_cache 表必须为空", 0, dao.count())
            assertEquals(AuthState.Anonymous, manager.authState.value)
            assertEquals(SessionData(), storage.loadSession())
        }

    private class UnusedEndpointClient : TokenEndpointClient {
        override suspend fun exchangeCode(code: String): TokenExchangeResult = throw TokenExchangeException("not used")
    }
}
