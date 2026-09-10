package com.yumiru11.githubapp.core.markdown.webview

import android.net.Uri
import android.webkit.WebResourceRequest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 私有图片代理白名单与转发链（#169 / L16 的验收断言）。
 *
 * 复核目标：白名单既不漏 GitHub 现役图床（否则私有仓库图裂），
 * 也不整站放行 github.com（否则 token 会跟随任意 github.com 资源请求外发）。
 *
 * 测试手法：MockWebServer 监听 127.0.0.1，但请求 URL 的 **host 必须是白名单域名**
 * （否则拦截器直接早返回，根本不会发起请求）。因此给 OkHttpClient 装一个把所有
 * 域名解析到 127.0.0.1 的 [Dns]，URL 用 `http://raw.githubusercontent.com:<mockPort>/…`。
 * 另外所有 takeRequest 都带超时——曾因 URL host 不在白名单导致 takeRequest 永久阻塞
 * （本地实测挂死 20 分钟），超时后失败比挂死可诊断得多。
 */
@RunWith(RobolectricTestRunner::class)
class PrivateImageInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            OkHttpClient
                .Builder()
                .dns { listOf(InetAddress.getByName(LOOPBACK)) }
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** 白名单域名 + MockWebServer 端口：拦截器会真正走网络，落到 MockWebServer 上。 */
    private fun allowlistedUrl(
        path: String = "/img.png",
        host: String = "raw.githubusercontent.com",
    ): String = "http://$host:${server.port}$path"

    private fun takeRequest() = server.takeRequest(5, TimeUnit.SECONDS)

    // ── 纯函数白名单矩阵 ─────────────────────────────────────────────

    @Test
    fun isAllowlisted_githubImageHosts_returnsTrue() {
        val hosts =
            listOf(
                "raw.githubusercontent.com",
                "avatars.githubusercontent.com",
                "user-images.githubusercontent.com",
                "private-user-images.githubusercontent.com",
                "camo.githubusercontent.com",
                "media.githubusercontent.com",
                "objects.githubusercontent.com",
            )

        hosts.forEach { host ->
            assertTrue("allowlisted: $host", PrivateImageInterceptor.isAllowlisted(host, "/x.png"))
        }
    }

    @Test
    fun isAllowlisted_uppercaseHost_returnsTrue() {
        assertTrue(PrivateImageInterceptor.isAllowlisted("Raw.GitHubUserContent.com", "/x.png"))
    }

    @Test
    fun isAllowlisted_attachmentPathOnGithubDotCom_returnsTrue() {
        assertTrue(
            PrivateImageInterceptor.isAllowlisted(
                "github.com",
                "/user-attachments/assets/01234567-89ab-cdef-0123-456789abcdef",
            ),
        )
    }

    @Test
    fun isAllowlisted_nonAttachmentPathOnGithubDotCom_returnsFalse() {
        assertFalse(PrivateImageInterceptor.isAllowlisted("github.com", "/octocat/Hello-World"))
        assertFalse(PrivateImageInterceptor.isAllowlisted("github.com", "/login/oauth/authorize"))
    }

    @Test
    fun isAllowlisted_lookalikeAndUnrelatedHosts_returnsFalse() {
        val hosts =
            listOf(
                "evil.example.com",
                "raw.githubusercontent.com.evil.example.com",
                "gist.githubusercontent.com",
                "api.github.com",
                "githubusercontent.com",
            )

        hosts.forEach { host ->
            assertFalse("not allowlisted: $host", PrivateImageInterceptor.isAllowlisted(host, "/x.png"))
        }
    }

    @Test
    fun isAllowlisted_attachmentPrefixLookalikePath_returnsFalse() {
        // 前缀必须整体匹配，"/user-attachments-evil/" 不算
        assertFalse(PrivateImageInterceptor.isAllowlisted("github.com", "/user-attachments-evil/x"))
    }

    // ── 转发行为 ─────────────────────────────────────────────────────

    @Test
    fun intercept_allowlistedHostWithToken_injectsBearerHeaderAndReturnsBody() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("PNGDATA")
                .addHeader("Content-Type", "image/png")
                .build(),
        )
        val interceptor = PrivateImageInterceptor({ "ghp_token" }, client)

        val response = interceptor.intercept(request(allowlistedUrl()))

        assertNotNull(response)
        assertEquals("image/png", response!!.mimeType)
        assertEquals("PNGDATA", response.data.readBytes().decodeToString())
        val recorded = takeRequest()
        assertNotNull("请求应到达 MockWebServer", recorded)
        assertEquals("Bearer ghp_token", recorded!!.headers["Authorization"])
    }

    @Test
    fun intercept_existingAuthorizationHeaderFromWebView_isReplaced() {
        server.enqueue(MockResponse.Builder().body("x").build())
        val interceptor = PrivateImageInterceptor({ "real-token" }, client)

        interceptor.intercept(
            request(
                url = allowlistedUrl(),
                headers = mapOf("Authorization" to "Bearer attacker-supplied"),
            ),
        )

        val recorded = takeRequest()
        assertNotNull("请求应到达 MockWebServer", recorded)
        assertEquals("Bearer real-token", recorded!!.headers["Authorization"])
    }

    @Test
    fun intercept_guestWithoutToken_returnsNullAndSkipsNetwork() {
        val interceptor = PrivateImageInterceptor({ null }, client)

        val response = interceptor.intercept(request(allowlistedUrl()))

        assertNull(response)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun intercept_nonAllowlistedHost_returnsNullAndSkipsNetwork() {
        val interceptor = PrivateImageInterceptor({ "ghp_token" }, client)

        val response = interceptor.intercept(request("https://evil.example.com/img.png"))

        assertNull(response)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun intercept_postMethod_returnsNullAndSkipsNetwork() {
        val interceptor = PrivateImageInterceptor({ "ghp_token" }, client)

        val response = interceptor.intercept(request(allowlistedUrl(), method = "POST"))

        assertNull(response)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun intercept_networkFailure_returnsNullWithoutThrowing() {
        val interceptor = PrivateImageInterceptor({ "ghp_token" }, client)

        // 白名单域名 + 已关闭端口 → 连接失败，必须吞掉异常返回 null（否则整页渲染被打断）
        val response = interceptor.intercept(request("http://raw.githubusercontent.com:1/img.png"))

        assertNull(response)
    }

    private fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
    ): WebResourceRequest =
        FakeWebResourceRequest(
            url = Uri.parse(url),
            method = method,
            headers = headers,
        )

    private class FakeWebResourceRequest(
        private val url: Uri,
        private val method: String,
        private val headers: Map<String, String>,
    ) : WebResourceRequest {
        override fun getUrl(): Uri = url

        override fun isForMainFrame(): Boolean = false

        override fun isRedirect(): Boolean = false

        override fun hasGesture(): Boolean = false

        override fun getMethod(): String = method

        override fun getRequestHeaders(): MutableMap<String, String> = headers.toMutableMap()
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
