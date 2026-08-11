package com.yumiru11.githubapp.core.githubrest.auth

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 认证头注入拦截器测试（plan.md §4.2/§4.3）。
 *
 * 有 token → Authorization: Bearer；游客（无 token）→ 不注入 Authorization。
 */
class AuthTokenInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun clientWith(provider: TokenProvider): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(AuthTokenInterceptor(provider))
            .build()

    @Test
    fun intercept_tokenPresent_addsBearerAuthorizationHeader() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        clientWith { "test-token-123" }
            .newCall(Request.Builder().url(server.url("/user")).build())
            .execute()
            .close()

        assertEquals("Bearer test-token-123", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun intercept_guestMode_doesNotAddAuthorizationHeader() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        clientWith(GuestTokenProvider())
            .newCall(Request.Builder().url(server.url("/repos/octocat/Hello-World")).build())
            .execute()
            .close()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun intercept_existingAuthorizationHeader_isReplacedByProviderToken() {
        server.enqueue(MockResponse.Builder().body("{}").build())

        clientWith { "fresh-token" }
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/user"))
                    .header("Authorization", "Bearer stale-token")
                    .build(),
            ).execute()
            .close()

        val values = server.takeRequest().headers.values("Authorization")
        assertEquals(1, values.size)
        assertEquals("Bearer fresh-token", values.first())
    }
}
