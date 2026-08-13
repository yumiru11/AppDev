package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST 事件 DTO（GET /users/{login}/received_events）。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 * 字段为 API 子集，ignoreUnknownKeys 容忍 GitHub 未来新增字段。
 * payload 按事件类型携带不同子对象，统一用可空字段承接。
 */
@Serializable
data class EventDto(
    val id: String,
    val type: String,
    val actor: EventActorDto,
    val repo: EventRepoDto,
    val payload: EventPayloadDto? = null,
    val createdAt: String? = null,
)

/** 事件触发者（actor）子对象。 */
@Serializable
data class EventActorDto(
    val login: String,
    val avatarUrl: String? = null,
)

/** 事件所属仓库子对象（name 格式为 "owner/repo"）。 */
@Serializable
data class EventRepoDto(
    val name: String,
)

/**
 * 事件载荷子对象（按事件类型不同携带不同字段）。
 *
 * - IssuesEvent / PullRequestEvent：action + issue/pullRequest
 * - IssueCommentEvent：action + comment（含 issue 引用）
 * - PushEvent：commits + size
 * - WatchEvent / ForkEvent：无额外字段
 */
@Serializable
data class EventPayloadDto(
    val action: String? = null,
    val issue: EventIssueDto? = null,
    val pullRequest: EventPullRequestDto? = null,
    val comment: EventCommentDto? = null,
    val commits: List<EventCommitDto>? = null,
    val size: Int? = null,
)

/** Issue 子对象（IssuesEvent / IssueCommentEvent 载荷内）。 */
@Serializable
data class EventIssueDto(
    val title: String? = null,
    val number: Int? = null,
    val htmlUrl: String? = null,
)

/** Pull Request 子对象（PullRequestEvent 载荷内）。 */
@Serializable
data class EventPullRequestDto(
    val title: String? = null,
    val number: Int? = null,
    val htmlUrl: String? = null,
)

/** 评论子对象（IssueCommentEvent 载荷内）。 */
@Serializable
data class EventCommentDto(
    val body: String? = null,
    val htmlUrl: String? = null,
)

/** 提交子对象（PushEvent 载荷内）。 */
@Serializable
data class EventCommitDto(
    val message: String? = null,
    val sha: String? = null,
)
