package com.yumiru11.githubapp.core.githubrest.http

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ETag 条件请求缓存拦截器测试（spec seam ②：304 Not Modified）。
 *
 * 行为：200 + ETag → 按 URL 缓存响应体；再次请求携带 If-None-Match；
 * 服务端 304 → 回放缓存体为 200（调用方无感知）。
 *
 * 另覆盖 issue #165 / L13 的限流观测：同一条响应链上录制 x-ratelimit-* 快照
 * （304 回放会丢头，必须在回放前录制）。
 */
class EtagCacheInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryEtagStore
    private lateinit var rateLimitStore: InMemoryRateLimitStore
    private lateinit var client: OkHttpClient
    private val scopeProvider = EtagScopeProvider { TEST_SCOPE }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemoryEtagStore()
        rateLimitStore = InMemoryRateLimitStore()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(EtagCacheInterceptor(store, rateLimitStore, scopeProvider))
                .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun get(path: String): okhttp3.Response = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun intercept_200WithEtag_storesBodyAndEtag() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"name":"Hello-World"}""")
                .addHeader("ETag", """"abc123"""")
                .addHeader("Content-Type", "application/json")
                .build(),
        )

        get("/repos/octocat/Hello-World").close()

        val entry = store.get(TEST_SCOPE, "GET", server.url("/repos/octocat/Hello-World").toString())
        assertEquals(""""abc123"""", entry?.etag)
        assertEquals("""{"name":"Hello-World"}""", entry?.body)
    }

    @Test
    fun intercept_secondRequest_sendsIfNoneMatchFromCache() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"name":"Hello-World"}""")
                .addHeader("ETag", """"abc123"""")
                .build(),
        )
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 304 Not Modified").build())

        get("/repos/octocat/Hello-World").close()
        get("/repos/octocat/Hello-World").close()

        server.takeRequest() // 首次请求无 If-None-Match
        val second = server.takeRequest()
        assertEquals(""""abc123"""", second.headers["If-None-Match"])
    }

    @Test
    fun intercept_304Response_replaysCachedBodyAs200() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"stargazers_count":42}""")
                .addHeader("ETag", """"v1"""")
                .addHeader("Content-Type", "application/json")
                .build(),
        )
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 304 Not Modified").build())

        get("/repos/octocat/Hello-World").close()
        val replayed = get("/repos/octocat/Hello-World")

        assertEquals(200, replayed.code)
        assertEquals("""{"stargazers_count":42}""", replayed.body.string())
        assertEquals(""""v1"""", replayed.header("ETag"))
    }

    @Test
    fun intercept_200WithoutEtag_isNotCached() {
        server.enqueue(MockResponse.Builder().body("""{"login":"octocat"}""").build())

        get("/user").close()

        assertNull(store.get(TEST_SCOPE, "GET", server.url("/user").toString()))
    }

    @Test
    fun intercept_nonGetRequest_skipsCacheEntirely() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/markdown"))
                    .post("body".toRequestBody(null))
                    .build(),
            ).execute()
            .close()

        assertNull(server.takeRequest().headers["If-None-Match"])
        assertNull(store.get(TEST_SCOPE, "GET", server.url("/markdown").toString()))
    }

    @Test
    fun intercept_responseWithRateLimitHeaders_recordsSnapshot() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("{}")
                .addHeader("x-ratelimit-limit", "30")
                .addHeader("x-ratelimit-remaining", "7")
                .addHeader("x-ratelimit-reset", "1800000000")
                .addHeader("x-ratelimit-resource", "search")
                .build(),
        )

        get("/search/repositories?q=kotlin").close()

        val snapshot = rateLimitStore.snapshot.value
        assertEquals(30, snapshot?.limit)
        assertEquals(7, snapshot?.remaining)
        assertEquals("search", snapshot?.resource)
    }

    @Test
    fun intercept_304Replay_recordsRateLimitHeadersFromOriginResponse() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"name":"Hello-World"}""")
                .addHeader("ETag", """"abc123"""")
                .addHeader("x-ratelimit-limit", "5000")
                .addHeader("x-ratelimit-remaining", "4200")
                .addHeader("x-ratelimit-reset", "1800000000")
                .addHeader("x-ratelimit-resource", "core")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .status("HTTP/1.1 304 Not Modified")
                .addHeader("x-ratelimit-limit", "5000")
                .addHeader("x-ratelimit-remaining", "4199")
                .addHeader("x-ratelimit-reset", "1800000000")
                .addHeader("x-ratelimit-resource", "core")
                .build(),
        )

        get("/repos/octocat/Hello-World").close()
        val replayed = get("/repos/octocat/Hello-World")

        // 回放后的 200 不带限流头，但 304 原始响应的配额已被录制
        assertEquals(200, replayed.code)
        assertEquals(4199, rateLimitStore.snapshot.value?.remaining)
    }

    @Test
    fun intercept_nonGetRequest_recordsRateLimitHeaders() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("{}")
                .addHeader("x-ratelimit-limit", "5000")
                .addHeader("x-ratelimit-remaining", "4990")
                .addHeader("x-ratelimit-reset", "1800000000")
                .build(),
        )

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/markdown"))
                    .post("body".toRequestBody(null))
                    .build(),
            ).execute()
            .close()

        assertEquals(4990, rateLimitStore.snapshot.value?.remaining)
    }

    @Test
    fun intercept_304WithoutCacheEntry_returnsOriginal304() {
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 304 Not Modified").build())

        val response = get("/repos/octocat/Hello-World")

        assertEquals(304, response.code)
    }

    @Test
    fun intercept_responseWithNoStore_isNotCached() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"private":true}""")
                .addHeader("ETag", """"priv1"""")
                .addHeader("Cache-Control", "private, no-store")
                .build(),
        )

        get("/user").close()

        assertNull(store.get(TEST_SCOPE, "GET", server.url("/user").toString()))
    }

    @Test
    fun intercept_differentScope_doesNotSendIfNoneMatchNorShareEntry() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"secret":"account-a"}""")
                .addHeader("ETag", """"a1"""")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .body("""{"secret":"account-b"}""")
                .addHeader("ETag", """"b1"""")
                .build(),
        )

        get("/user").close()

        val accountBClient =
            OkHttpClient
                .Builder()
                .addInterceptor(EtagCacheInterceptor(store, rateLimitStore, EtagScopeProvider { "account-b" }))
                .build()
        accountBClient
            .newCall(Request.Builder().url(server.url("/user")).build())
            .execute()
            .close()

        server.takeRequest()
        val bRequest = server.takeRequest()
        assertNull("B 不得复用 A 的 ETag", bRequest.headers["If-None-Match"])
        assertEquals(""""a1"""", store.get(TEST_SCOPE, "GET", server.url("/user").toString())?.etag)
        assertEquals(""""b1"""", store.get("account-b", "GET", server.url("/user").toString())?.etag)
    }

    private companion object {
        const val TEST_SCOPE = "account-a"
    }
}
