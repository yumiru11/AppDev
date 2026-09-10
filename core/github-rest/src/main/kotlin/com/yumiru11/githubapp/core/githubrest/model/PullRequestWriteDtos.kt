package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// PR 写操作请求 DTO（#163 L03）。纯 Kotlin + kotlinx-serialization
// （架构护栏：model 包禁 android import）；请求体经 SnakeCase 命名策略序列化。

/**
 * PATCH /repos/{owner}/{repo}/pulls/{number}：更新 PR 请求体（#163 L03）。
 *
 * 字段语义与 [UpdateIssueRequest] 完全一致：**仅序列化非空字段**
 * （[UpdatePullRequestRequestSerializer]）——未携带的字段 GitHub 保持原值；
 * 清空正文发空串（显式 null 会被 GitHub 拒绝）。
 *
 * @param title 新标题（null = 不修改）
 * @param body 新正文（null = 不修改）
 * @param state "open"（重开）/ "closed"（关闭）；null = 不修改
 */
@Serializable(with = UpdatePullRequestRequestSerializer::class)
data class UpdatePullRequestRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
)

/** [UpdatePullRequestRequest] 序列化器：跳过 null 字段（复用 [UpdateIssueRequestSerializer] 语义）。 */
internal object UpdatePullRequestRequestSerializer : KSerializer<UpdatePullRequestRequest> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("UpdatePullRequestRequest") {
            element<String>("title", isOptional = true)
            element<String>("body", isOptional = true)
            element<String>("state", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: UpdatePullRequestRequest,
    ) {
        encoder.encodeSerializableValue(JsonObject.serializer(), value.toJsonObject())
    }

    override fun deserialize(decoder: Decoder): UpdatePullRequestRequest = UpdatePullRequestRequest()

    private fun UpdatePullRequestRequest.toJsonObject(): JsonObject =
        buildJsonObject {
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            state?.let { put("state", it) }
        }
}
