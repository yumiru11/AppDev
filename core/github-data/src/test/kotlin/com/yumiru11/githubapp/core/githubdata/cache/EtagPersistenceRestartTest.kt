package com.yumiru11.githubapp.core.githubdata.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.AppDatabase
import com.yumiru11.githubapp.core.githubrest.http.EtagCacheInterceptor
import com.yumiru11.githubapp.core.githubrest.http.EtagScopeProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryRateLimitStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 跨进程持久化验证（DATA-1）：模拟「App 重启」——关闭并重开文件 Room 数据库、构造**新的**
 * RoomEtagStore 实例，验证第二次请求携带 If-None-Match 且 304 能回放上次缓存的响应体。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EtagPersistenceRestartTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private val scopeProvider = EtagScopeProvider { "account-a" }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        server.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun restart_newStoreInstance_sendsIfNoneMatchAndReplaysCached304Body() {
        server.enqueue(
            MockResponse
                .Builder()
                .body(BODY)
                .addHeader("ETag", ETAG)
                .addHeader("Content-Type", "application/json")
                .build(),
        )
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 304 Not Modified").build())

        val url = server.url("/repos/octocat/Hello-World")

        openProcess { client ->
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals(200, response.code)
            }
        }

        openProcess { client ->
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals("重启后 304 应回放为 200", 200, response.code)
                assertEquals(BODY, response.body.string())
            }
        }

        server.takeRequest()
        assertEquals("重启后应携带上次持久化的 ETag", ETAG, server.takeRequest().headers["If-None-Match"])
    }

    private fun openProcess(block: (OkHttpClient) -> Unit) {
        val db =
            Room
                .databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .build()
        try {
            val store = RoomEtagStore(db.etagCacheDao())
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(EtagCacheInterceptor(store, InMemoryRateLimitStore(), scopeProvider))
                    .build()
            block(client)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "etag-restart-test.db"
        const val ETAG = "\"v1\""
        const val BODY = "{\"stargazers_count\":42}"
    }
}
