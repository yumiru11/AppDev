package com.yumiru11.githubapp.core.githubdata.retry

import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * 重试策略测试（issue #6：429 Retry-After 生效 / 5xx 指数退避 / 超次数失败）。
 *
 * delay 函数注入虚拟时间，测试零等待。
 */
class RetryPolicyTest {
    @Test
    fun execute_rateLimitedWithRetryAfter_waitsRetryAfterSeconds() =
        runTest {
            val delays = mutableListOf<Long>()
            val policy = retryPolicy(maxAttempts = 3, delayFn = { delays += it })
            var calls = 0

            val result =
                policy.execute {
                    calls += 1
                    if (calls == 1) throw httpException(429, retryAfter = "3")
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(2, calls)
            assertEquals(listOf(3_000L), delays)
        }

    @Test
    fun execute_serverErrors_exponentialBackoffThenSucceeds() =
        runTest {
            val delays = mutableListOf<Long>()
            val policy = retryPolicy(maxAttempts = 4, baseDelayMillis = 100, delayFn = { delays += it })
            var calls = 0

            val result =
                policy.execute {
                    calls += 1
                    if (calls <= 2) throw httpException(500)
                    "recovered"
                }

            assertEquals("recovered", result)
            assertEquals(3, calls)
            assertEquals(listOf(100L, 200L), delays)
        }

    @Test
    fun execute_attemptsExhausted_throwsRequestExceptionWithServerError() =
        runTest {
            val delays = mutableListOf<Long>()
            val policy = retryPolicy(maxAttempts = 3, delayFn = { delays += it })
            var calls = 0

            val exception =
                assertFailsWith<GitHubRequestException> {
                    policy.execute {
                        calls += 1
                        throw httpException(503)
                    }
                }

            assertEquals(3, calls)
            assertEquals(GitHubError.Server(code = 503), exception.error)
            assertEquals(listOf(250L, 500L), delays)
        }

    @Test
    fun execute_networkError_isRetried() =
        runTest {
            val policy = retryPolicy(maxAttempts = 2, delayFn = { })
            var calls = 0

            val result =
                policy.execute {
                    calls += 1
                    if (calls == 1) throw IOException("connection reset")
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(2, calls)
        }

    @Test
    fun execute_nonRetryableError_failsImmediatelyWithoutDelay() =
        runTest {
            val delays = mutableListOf<Long>()
            val policy = retryPolicy(maxAttempts = 3, delayFn = { delays += it })
            var calls = 0

            val exception =
                assertFailsWith<GitHubRequestException> {
                    policy.execute {
                        calls += 1
                        throw httpException(401)
                    }
                }

            assertEquals(1, calls)
            assertEquals(GitHubError.Unauthorized, exception.error)
            assertTrue("401 不应触发任何重试等待", delays.isEmpty())
        }

    private fun retryPolicy(
        maxAttempts: Int,
        baseDelayMillis: Long = 250,
        delayFn: suspend (Long) -> Unit,
    ): RetryPolicy =
        RetryPolicy(
            maxAttempts = maxAttempts,
            baseDelayMillis = baseDelayMillis,
            delayFn = delayFn,
        )

    private fun httpException(
        code: Int,
        retryAfter: String? = null,
    ): HttpException {
        val headers =
            retryAfter
                ?.let { okhttp3.Headers.headersOf("Retry-After", it) }
                ?: okhttp3.Headers.headersOf()
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
                .headers(headers)
                .body(body)
                .build()
        return HttpException(Response.error<Any>(body, rawResponse))
    }
}
