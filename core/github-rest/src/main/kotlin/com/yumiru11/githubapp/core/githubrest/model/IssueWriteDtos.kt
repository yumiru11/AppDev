package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Issue 写操作请求/响应 DTO 集合（T14）。纯 Kotlin + kotlinx-serialization
// （架构护栏：model 包禁 android import）；请求体经 SnakeCase 命名策略序列化。

/** POST /repos/{owner}/{repo}/issues：创建 Issue 请求体 */
@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String? = null,
    val labels: List<String>? = null,
)

/** POST /repos/{owner}/{repo}/issues/{number}/comments：新增评论请求体 */
@Serializable
data class CreateCommentRequest(
    val body: String,
)

/** POST .../reactions：新增反应请求体（content 取值：+1/-1/laugh/hooray/confused/heart/rocket/eyes） */
@Serializable
data class CreateReactionRequest(
    val content: String,
)

/**
 * PATCH /repos/{owner}/{repo}/issues/{number}：更新 Issue 请求体。
 *
 * 字段全部可空且**仅序列化非空字段**（[UpdateIssueRequestSerializer]）：
 * GitHub 对 labels/assignees/milestone 的显式 null 语义是「清空」，若把未变更字段
 * 以 null 发送会误清数据，故必须按需携带字段。
 */
@Serializable(with = UpdateIssueRequestSerializer::class)
data class UpdateIssueRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    val labels: List<String>? = null,
    val assignees: List<String>? = null,
    val milestone: Long? = null,
)

/** [UpdateIssueRequest] 序列化器：跳过 null 字段（GitHub null 语义 = 清空，见上）。 */
internal object UpdateIssueRequestSerializer : KSerializer<UpdateIssueRequest> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("UpdateIssueRequest") {
            element<String>("title", isOptional = true)
            element<String>("body", isOptional = true)
            element<String>("state", isOptional = true)
            element<List<String>>("labels", isOptional = true)
            element<List<String>>("assignees", isOptional = true)
            element<Long>("milestone", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: UpdateIssueRequest,
    ) {
        encoder.encodeSerializableValue(JsonObject.serializer(), value.toJsonObject())
    }

    override fun deserialize(decoder: Decoder): UpdateIssueRequest = UpdateIssueRequest()

    private fun UpdateIssueRequest.toJsonObject(): JsonObject =
        buildJsonObject {
            title?.let { put("title", it) }
            body?.let { put("body", it) }
            state?.let { put("state", it) }
            labels?.let { putJsonArray("labels", it) }
            assignees?.let { putJsonArray("assignees", it) }
            milestone?.let { put("milestone", it) }
        }

    private fun JsonObjectBuilder.putJsonArray(
        key: String,
        values: List<String>,
    ) {
        put(key, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }
}

/** 评论响应 DTO（create/update comment 返回完整评论对象） */
@Serializable
data class IssueCommentDto(
    val id: Long,
    val body: String? = null,
    val user: UserDto? = null,
    val htmlUrl: String? = null,
    val createdAt: String? = null,
    val reactions: ReactionsDto? = null,
)

/** 反应响应 DTO（add reaction 返回完整反应对象，id 供删除用） */
@Serializable
data class ReactionDto(
    val id: Long,
    val content: String,
    val user: UserDto? = null,
)
