package com.yumiru11.githubapp.core.githubgraphql.scalar

import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter
import com.apollographql.apollo.api.json.BufferedSourceJsonReader
import com.apollographql.apollo.exception.JsonDataException
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * [InstantAdapter] 标量适配器边界测试（checklist C6）。
 *
 * 覆盖：合法 ISO-8601（含 epoch/小数秒/时区偏移）、null token、畸形字符串、
 * toJson 输出与往返一致性。
 */
class InstantAdapterTest {
    private fun fromJson(json: String): Instant =
        InstantAdapter.fromJson(
            BufferedSourceJsonReader(Buffer().writeUtf8(json)),
            CustomScalarAdapters.Empty,
        )

    private fun toJson(value: Instant): String {
        val sink = Buffer()
        InstantAdapter.toJson(BufferedSinkJsonWriter(sink, "test"), CustomScalarAdapters.Empty, value)
        return sink.readUtf8()
    }

    @Test
    fun fromJson_validIsoInstant_parses() {
        assertEquals(Instant.parse("2026-08-12T01:00:00Z"), fromJson("\"2026-08-12T01:00:00Z\""))
    }

    @Test
    fun fromJson_epochIsoInstant_parsesToEpoch() {
        assertEquals(Instant.EPOCH, fromJson("\"1970-01-01T00:00:00Z\""))
    }

    @Test
    fun fromJson_fractionalSeconds_parsesWithNanos() {
        assertEquals(Instant.parse("2026-08-12T01:00:00.123456789Z"), fromJson("\"2026-08-12T01:00:00.123456789Z\""))
    }

    @Test
    fun fromJson_offsetTimezone_parsesToInstant() {
        // +08:00 偏移解析为同一时刻（Instant.parse 语义，GitHub 一般返回 Z）
        assertEquals(Instant.parse("2026-08-11T17:00:00Z"), fromJson("\"2026-08-12T01:00:00+08:00\""))
    }

    @Test
    fun fromJson_nullToken_throwsJsonDataException() {
        val e = assertThrows(JsonDataException::class.java) { fromJson("null") }
        assertTrue("null token 报错应说明期望字符串，实际：${e.message}", e.message?.contains("string") == true)
    }

    @Test
    fun fromJson_malformedString_throwsDateTimeParseException() {
        assertThrows(DateTimeParseException::class.java) { fromJson("\"not-a-date\"") }
    }

    @Test
    fun toJson_instant_writesIsoString() {
        assertEquals("\"2026-08-12T01:00:00Z\"", toJson(Instant.parse("2026-08-12T01:00:00Z")))
    }

    @Test
    fun toJson_epoch_writesEpochIsoString() {
        assertEquals("\"1970-01-01T00:00:00Z\"", toJson(Instant.EPOCH))
    }

    @Test
    fun toJson_fractionalSecond_writesNormalizedIsoString() {
        assertEquals("\"2026-08-12T01:00:00.500Z\"", toJson(Instant.parse("2026-08-12T01:00:00.5Z")))
    }

    @Test
    fun roundTrip_toJsonThenFromJson_returnsSameInstant() {
        val original = Instant.parse("2026-08-12T01:00:00.5Z")
        assertEquals(original, fromJson(toJson(original)))
    }
}
