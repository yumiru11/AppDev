package com.yumiru11.githubapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.session.SessionCacheCleaner
import com.yumiru11.githubapp.core.githubdata.repository.RepositoryRepository
import com.yumiru11.githubapp.core.githubdata.user.UserRepository
import com.yumiru11.githubapp.core.githubrest.http.EtagScopeProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Hilt 图装配冒烟测试（Robolectric + HiltTestApplication）。
 *
 * 验证 T5 所有模块的 DI 接线能组成完整图：仓库层（GraphQL/REST 通道）、
 * Room 数据库、Preferences DataStore。测试用 @HiltAndroidTest 走真实
 * HiltAndroidTestRunner 生成并注入图，非手工构造。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HiltGraphTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var userRepository: UserRepository

    @Inject lateinit var repositoryRepository: RepositoryRepository

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var database: AppDatabase

    /** app 装配层的 OAuthConfig 绑定（BuildConfig.OAUTH_CLIENT_ID 注入）——防绑定丢失回退占位符。 */
    @Inject lateinit var oauthConfig: OAuthConfig

    /** 持久化 ETag 缓存绑定（DATA-1）：缺失会回退到无缓存实现，必须结构化守护。 */
    @Inject lateinit var etagStore: EtagStore

    /** ETag 账号作用域绑定（DATA-1）：按凭据派生，缺失会回退游客作用域。 */
    @Inject lateinit var etagScopeProvider: EtagScopeProvider

    /** 登出清空集（DATA-1）：为空说明持久化缓存未接入 signOut，私有正文会残留。 */
    @Inject lateinit var sessionCacheCleaners: Set<@JvmSuppressWildcards SessionCacheCleaner>

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun hiltGraph_injectsAllRepositoriesAndStores() {
        assertNotNull("UserRepository 应可注入", userRepository)
        assertNotNull("RepositoryRepository 应可注入", repositoryRepository)
        assertNotNull("DataStore 应可注入", dataStore)
        assertNotNull("AppDatabase 应可注入", database)
        assertNotNull("AppDatabase DAO 应可获取", database.cachedRepositoryDao())
        assertNotNull("OAuthConfig 应可注入（app 装配层）", oauthConfig)
        assertNotNull("EtagStore 应可注入（持久化缓存）", etagStore)
        assertNotNull("EtagScopeProvider 应可注入（账号作用域）", etagScopeProvider)
        assertTrue("登出清空集不得为空（持久化缓存须接入 signOut）", sessionCacheCleaners.isNotEmpty())
    }
}
