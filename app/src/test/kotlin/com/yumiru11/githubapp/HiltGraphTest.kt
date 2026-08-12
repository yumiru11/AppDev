package com.yumiru11.githubapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.githubdata.repository.RepositoryRepository
import com.yumiru11.githubapp.core.githubdata.user.UserRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
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
    }
}
