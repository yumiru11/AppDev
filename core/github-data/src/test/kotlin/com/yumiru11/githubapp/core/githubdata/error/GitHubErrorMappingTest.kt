package com.yumiru11.githubapp.core.githubdata.error

import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
