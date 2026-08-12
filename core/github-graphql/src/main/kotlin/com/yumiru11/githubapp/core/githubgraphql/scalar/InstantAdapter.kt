package com.yumiru11.githubapp.core.githubgraphql.scalar

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter
import java.time.Instant

/**
 * GitHub `DateTime`/`PreciseDateTime` 标量 → [Instant] 适配器（plan.md §4.4）。
 *
 * GitHub 返回 ISO-8601（如 2026-08-12T01:00:00Z），[Instant.parse] 直接解析；
 * minSdk 26 原生支持 java.time，无需 kotlinx-datetime 依赖。
 */
object InstantAdapter : Adapter<Instant> {
    override fun fromJson(
        reader: JsonReader,
        customScalarAdapters: CustomScalarAdapters,
    ): Instant = Instant.parse(reader.nextString())

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        value: Instant,
    ) {
        writer.value(value.toString())
    }
}
