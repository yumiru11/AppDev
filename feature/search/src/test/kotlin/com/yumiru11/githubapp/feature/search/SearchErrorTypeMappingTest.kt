package com.yumiru11.githubapp.feature.search

import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * Throwable → SearchErrorType 映射单测（T18 验收第 5 条：429 限流友好提示）。
 *
 * 覆盖：429/403+Retry-After → RATE_LIMITED、401 → UNAUTHORIZED、IO → NETWORK、
 * 未知 → UNKNOWN、GitHubRequestException 包装解包。
 */
class SearchErrorTypeMappingTest {
    @Test
    fun http429_mapsToRateLimited() {
        assertEquals(SearchErrorType.RATE_LIMITED, httpException(429).toSearchErrorType())
    }

    @Test
    fun gitHubRequestExceptionRateLimited_mapsToRateLimited() {
        val wrapped = GitHubRequestException(GitHubError.RateLimited(retryAfterSeconds = 60))

        assertEquals(SearchErrorType.RATE_LIMITED, wrapped.toSearchErrorType())
    }

    @Test
    fun http401_mapsToUnauthorized() {
        assertEquals(SearchErrorType.UNAUTHORIZED, httpException(401).toSearchErrorType())
    }

    @Test
    fun ioException_mapsToNetwork() {
        assertEquals(SearchErrorType.NETWORK, IOException("timeout").toSearchErrorType())
    }

    @Test
    fun unknownException_mapsToUnknown() {
        assertEquals(SearchErrorType.UNKNOWN, IllegalStateException("boom").toSearchErrorType())
    }

    @Test
    fun serverError_mapsToUnknown() {
        assertEquals(SearchErrorType.UNKNOWN, httpException(500).toSearchErrorType())
    }

    private fun httpException(code: Int): HttpException {
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
                .body(body)
                .build()
        return HttpException(retrofit2.Response.error<Any>(body, rawResponse))
    }
}
