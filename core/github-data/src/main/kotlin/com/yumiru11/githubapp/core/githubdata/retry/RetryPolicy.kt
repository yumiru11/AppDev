package com.yumiru11.githubapp.core.githubdata.retry

import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow

private const val DEFAULT_MAX_ATTEMPTS = 3
private const val DEFAULT_BASE_DELAY_MILLIS = 250L
private const val MILLIS_PER_SECOND = 1_000L
private const val BACKOFF_FACTOR = 2.0

/**
 * 请求重试策略（issue #6：429 Retry-After 生效 / 5xx 指数退避 / 超次数失败）。
 *
 * 仅对 [GitHubError.isRetryable] 的错误（限流/服务端/网络）重试；
 * 429 优先使用 Retry-After 秒数，其余按 base * 2^(attempt-1) 指数退避。
 *
 * [delayFn] 可注入（测试用虚拟时间，零等待）。
 */
class RetryPolicy(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    /**
     * 执行 [block]，按策略重试；最终失败抛 [GitHubRequestException]。
     *
     * [CancellationException] 直抛不吞（协程取消语义）。
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            attempt += 1
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") t: Throwable,
            ) {
                // 归一化语义要求捕获任意异常（未知类型归 GitHubError.Unknown），故抑制 TooGenericExceptionCaught
                val error = t.asGitHubError()
                if (!error.isRetryable || attempt >= maxAttempts) {
                    throw GitHubRequestException(error, t)
                }
                delayFn(delayMillisFor(error, attempt))
            }
        }
    }

    private fun delayMillisFor(
        error: GitHubError,
        attempt: Int,
    ): Long =
        (error as? GitHubError.RateLimited)
            ?.retryAfterSeconds
            ?.let { it * MILLIS_PER_SECOND }
            ?: backoffMillis(attempt)

    private fun backoffMillis(attempt: Int): Long = (baseDelayMillis * BACKOFF_FACTOR.pow(attempt - 1)).toLong()
}
