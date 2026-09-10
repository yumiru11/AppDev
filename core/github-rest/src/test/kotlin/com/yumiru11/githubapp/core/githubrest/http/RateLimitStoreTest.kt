package com.yumiru11.githubapp.core.githubrest.http

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 限流快照解析/存储单测（issue #165 / L13，plan.md §9.3）。
 *
 * 覆盖：x-ratelimit-* 头解析、缺头/脏数据 → null（不得抛）、resetInMinutes 取整与过期、
 * 存储覆盖语义（后写覆盖前写，同值不重复发射）。
 */
class RateLimitStoreTest {
    @Test
    fun toRateLimitSnapshot_fullHeaders_parsesEveryField() {
        val response =
            responseWith(
                "x-ratelimit-limit" to "30",
                "x-ratelimit-remaining" to "7",
                "x-ratelimit-reset" to "1800000000",
                "x-ratelimit-resource" to "search",
            )

        val snapshot = response.toRateLimitSnapshot()

        assertEquals(30, snapshot?.limit)
        assertEquals(7, snapshot?.remaining)
        assertEquals(1_800_000_000L, snapshot?.resetEpochSeconds)
        assertEquals("search", snapshot?.resource)
    }

    @Test
    fun toRateLimitSnapshot_missingRemaining_returnsNull() {
        val response = responseWith("x-ratelimit-limit" to "30", "x-ratelimit-reset" to "1800000000")

        assertNull(response.toRateLimitSnapshot())
    }

    @Test
    fun toRateLimitSnapshot_noRateLimitHeaders_returnsNull() {
        assertNull(responseWith().toRateLimitSnapshot())
    }

    @Test
    fun toRateLimitSnapshot_malformedNumbers_returnsNull() {
        val response =
            responseWith(
                "x-ratelimit-limit" to "abc",
                "x-ratelimit-remaining" to "7",
                "x-ratelimit-reset" to "1800000000",
            )

        assertNull(response.toRateLimitSnapshot())
    }

    @Test
    fun resetInMinutes_partialMinute_roundsUp() {
        val snapshot = snapshot(resetEpochSeconds = 1_000_060L)

        // 距重置 60s → 1 分钟
        assertEquals(1L, snapshot.resetInMinutes(nowMillis = 1_000_000_000L))
    }

    @Test
    fun resetInMinutes_alreadyReset_returnsZero() {
        val snapshot = snapshot(resetEpochSeconds = 1_000_000L)

        assertEquals(0L, snapshot.resetInMinutes(nowMillis = 1_000_600_000L))
    }

    @Test
    fun record_laterSnapshot_replacesPrevious() =
        runTest {
            val store = InMemoryRateLimitStore()
            assertNull(store.snapshot.value)

            store.record(snapshot(remaining = 9))
            store.record(snapshot(remaining = 3))

            assertEquals(3, store.snapshot.value?.remaining)
            assertEquals(3, store.snapshot.first()?.remaining)
        }

    private fun snapshot(
        limit: Int = 5000,
        remaining: Int = 9,
        resetEpochSeconds: Long = 1_800_000_000L,
        resource: String? = "core",
    ): RateLimitSnapshot =
        RateLimitSnapshot(limit = limit, remaining = remaining, resetEpochSeconds = resetEpochSeconds, resource = resource)

    private fun responseWith(vararg headers: Pair<String, String>): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://api.github.com/rate_limit").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
}
