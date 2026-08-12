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
 */
class EtagCacheInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryEtagStore
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemoryEtagStore()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(EtagCacheInterceptor(store))
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

        val entry = store.get(server.url("/repos/octocat/Hello-World").toString())
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

        assertNull(store.get(server.url("/user").toString()))
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
        assertNull(store.get(server.url("/markdown").toString()))
    }

    @Test
    fun intercept_304WithoutCacheEntry_returnsOriginal304() {
        server.enqueue(MockResponse.Builder().status("HTTP/1.1 304 Not Modified").build())

        val response = get("/repos/octocat/Hello-World")

        assertEquals(304, response.code)
    }
}
