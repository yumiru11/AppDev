package com.yumiru11.githubapp.core.githubdata.error

import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 错误归一化测试（issue #6：MockWebServer 覆盖 401/403/404/409/422/429/5xx）。
 *
 * Retrofit 3 suspend 直抛 [HttpException]、Apollo 系列异常统一映射为 [GitHubError]。
 */
class GitHubErrorMappingTest {
    @Test
    fun asGitHubError_http401_mapsUnauthorized() {
        assertEquals(GitHubError.Unauthorized, httpException(401).asGitHubError())
    }

    @Test
    fun asGitHubError_http403_mapsForbidden() {
        assertEquals(GitHubError.Forbidden, httpException(403).asGitHubError())
    }

    @Test
    fun asGitHubError_http404_mapsNotFound() {
        assertEquals(GitHubError.NotFound, httpException(404).asGitHubError())
    }

    @Test
    fun asGitHubError_http409_mapsConflict() {
        assertEquals(GitHubError.Conflict, httpException(409).asGitHubError())
    }

    @Test
    fun asGitHubError_http422_mapsValidation() {
        assertTrue(httpException(422).asGitHubError() is GitHubError.Validation)
    }

    @Test
    fun asGitHubError_http429WithRetryAfter_mapsRateLimitedWithSeconds() {
        val error = httpException(429, Headers.headersOf("Retry-After", "3")).asGitHubError()

        assertEquals(GitHubError.RateLimited(retryAfterSeconds = 3L), error)
    }

    @Test
    fun asGitHubError_http429WithoutRetryAfter_mapsRateLimitedWithoutSeconds() {
        assertEquals(GitHubError.RateLimited(retryAfterSeconds = null), httpException(429).asGitHubError())
    }

    @Test
    fun asGitHubError_http500_mapsServer() {
        assertEquals(GitHubError.Server(code = 500), httpException(500).asGitHubError())
    }

    @Test
    fun asGitHubError_http502_mapsServerWithCode() {
        assertEquals(GitHubError.Server(code = 502), httpException(502).asGitHubError())
    }

    @Test
    fun asGitHubError_unknownHttpStatus_mapsUnknown() {
        assertTrue(httpException(418).asGitHubError() is GitHubError.Unknown)
    }

    @Test
    fun asGitHubError_ioException_mapsNetwork() {
        assertTrue(IOException("connection reset").asGitHubError() is GitHubError.Network)
    }

    @Test
    fun asGitHubError_apolloHttpException401_mapsUnauthorized() {
        val exception = ApolloHttpException(401, emptyList(), null, "", null)

        assertEquals(GitHubError.Unauthorized, exception.asGitHubError())
    }

    @Test
    fun asGitHubError_apolloHttpException429_parsesRetryAfterHeader() {
        val exception = ApolloHttpException(429, listOf(HttpHeader("Retry-After", "7")), null, "", null)

        assertEquals(GitHubError.RateLimited(retryAfterSeconds = 7L), exception.asGitHubError())
    }

    @Test
    fun asGitHubError_apolloNetworkException_mapsNetwork() {
        assertTrue(ApolloNetworkException("unreachable").asGitHubError() is GitHubError.Network)
    }

    @Test
    fun asGitHubError_unrelatedException_mapsUnknown() {
        assertTrue(IllegalStateException("boom").asGitHubError() is GitHubError.Unknown)
    }

    @Test
    fun asGitHubError_http503_mapsServer() {
        assertEquals(GitHubError.Server(code = 503), httpException(503).asGitHubError())
    }

    @Test
    fun asGitHubError_http599_mapsServerWithCode() {
        assertEquals(GitHubError.Server(code = 599), httpException(599).asGitHubError())
    }

    @Test
    fun asGitHubError_http499_belowServerRange_mapsUnknown() {
        assertTrue(httpException(499).asGitHubError() is GitHubError.Unknown)
    }

    @Test
    fun asGitHubError_http600_aboveServerRange_mapsUnknown() {
        assertTrue(httpException(600).asGitHubError() is GitHubError.Unknown)
    }

    @Test
    fun asGitHubError_http429NonNumericRetryAfter_mapsRateLimitedWithoutSeconds() {
        val error = httpException(429, Headers.headersOf("Retry-After", "abc")).asGitHubError()

        assertEquals(GitHubError.RateLimited(retryAfterSeconds = null), error)
    }

    @Test
    fun asGitHubError_http429NegativeRetryAfter_preservesValue() {
        // toLongOrNull 原样透传；文档记录当前行为（GitHub 不会下发负值）
        val error = httpException(429, Headers.headersOf("Retry-After", "-5")).asGitHubError()

        assertEquals(GitHubError.RateLimited(retryAfterSeconds = -5L), error)
    }

    @Test
    fun asGitHubError_http403WithRetryAfter_mapsRateLimited() {
        // 契约见 GitHubError.kt Forbidden 注释：带可解析 Retry-After 的 403 归入限流
        val error = httpException(403, Headers.headersOf("Retry-After", "3")).asGitHubError()

        assertEquals(GitHubError.RateLimited(retryAfterSeconds = 3L), error)
    }

    @Test
    fun asGitHubError_http403WithoutRetryAfter_mapsForbidden() {
        assertEquals(GitHubError.Forbidden, httpException(403).asGitHubError())
    }

    @Test
    fun asGitHubError_apolloHttpExceptionLowercaseRetryAfter_parsesHeader() {
        val exception = ApolloHttpException(429, listOf(HttpHeader("retry-after", "7")), null, "", null)

        assertEquals(GitHubError.RateLimited(retryAfterSeconds = 7L), exception.asGitHubError())
    }

    @Test
    fun asGitHubError_apolloHttpException403_mapsForbidden() {
        val exception = ApolloHttpException(403, emptyList(), null, "", null)

        assertEquals(GitHubError.Forbidden, exception.asGitHubError())
    }

    @Test
    fun asGitHubError_apolloHttpException500_mapsServer() {
        val exception = ApolloHttpException(500, emptyList(), null, "", null)

        assertEquals(GitHubError.Server(code = 500), exception.asGitHubError())
    }

    @Test
    fun asGitHubError_socketTimeoutException_mapsNetwork() {
        assertTrue(java.net.SocketTimeoutException("timeout").asGitHubError() is GitHubError.Network)
    }

    @Test
    fun asGitHubError_ioException_preservesCause() {
        val cause = IOException("connection reset")
        val error = cause.asGitHubError() as GitHubError.Network

        assertEquals(cause, error.cause)
    }

    @Test
    fun isRetryable_rateLimited_returnsTrue() {
        assertTrue(GitHubError.RateLimited(retryAfterSeconds = null).isRetryable)
    }

    @Test
    fun isRetryable_server_returnsTrue() {
        assertTrue(GitHubError.Server(code = 503).isRetryable)
    }

    @Test
    fun isRetryable_network_returnsTrue() {
        assertTrue(GitHubError.Network().isRetryable)
    }

    @Test
    fun isRetryable_unauthorized_returnsFalse() {
        assertFalse(GitHubError.Unauthorized.isRetryable)
    }

    @Test
    fun isRetryable_forbidden_returnsFalse() {
        assertFalse(GitHubError.Forbidden.isRetryable)
    }

    @Test
    fun isRetryable_notFound_returnsFalse() {
        assertFalse(GitHubError.NotFound.isRetryable)
    }

    @Test
    fun isRetryable_conflict_returnsFalse() {
        assertFalse(GitHubError.Conflict.isRetryable)
    }

    @Test
    fun isRetryable_validation_returnsFalse() {
        assertFalse(GitHubError.Validation.isRetryable)
    }

    @Test
    fun isRetryable_graphQl_returnsFalse() {
        assertFalse(GitHubError.GraphQl(messages = emptyList()).isRetryable)
    }

    @Test
    fun isRetryable_unknown_returnsFalse() {
        assertFalse(GitHubError.Unknown().isRetryable)
    }

    @Test
    fun githubRequestException_errorAndCause_preserved() {
        val cause = IllegalStateException("boom")
        val exception = GitHubRequestException(GitHubError.NotFound, cause)

        assertEquals(GitHubError.NotFound, exception.error)
        assertEquals(cause, exception.cause)
    }

    private fun httpException(
        code: Int,
        headers: Headers? = null,
    ): HttpException {
        val body = """{"message":"error"}""".toResponseBody("application/json".toMediaType())
        val rawResponse =
            okhttp3.Response
                .Builder()
                .request(
                    okhttp3.Request
                        .Builder()
                        .url("http://localhost/")
                        .build(),
                ).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message("error")
                .headers(headers ?: Headers.headersOf())
                .body(body)
                .build()
        return HttpException(Response.error<Any>(body, rawResponse))
    }
}
