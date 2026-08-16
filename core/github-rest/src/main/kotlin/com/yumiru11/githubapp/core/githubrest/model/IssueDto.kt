package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * GitHub REST Issue / 时间线 DTO 集合（T13）。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 * 字段为 API 子集：GitHubRestClient.createJson() 已配 SnakeCase 命名策略
 * （snake_case JSON ↔ camelCase 属性自动映射）+ ignoreUnknownKeys 容忍新增字段。
 */
@Serializable
data class IssueDto(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    val user: UserDto? = null,
    val labels: List<LabelDto> = emptyList(),
    val assignees: List<UserDto> = emptyList(),
    val milestone: MilestoneDto? = null,
    val reactions: ReactionsDto? = null,
    val comments: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val closedAt: String? = null,
    val htmlUrl: String? = null,
    /** 存在字段说明该 issue 实则关联 PR（GET /issues 同时返回 PR） */
    val pullRequest: JsonObject? = null,
    /** 归属仓库 API URL（搜索 items 特有：https://api.github.com/repos/{owner}/{repo}） */
    val repositoryUrl: String? = null,
)

@Serializable
data class LabelDto(
    val name: String,
    val color: String? = null,
)

@Serializable
data class MilestoneDto(
    val title: String,
    val state: String? = null,
    val description: String? = null,
)

@Serializable
data class ReactionsDto(
    val totalCount: Int = 0,
)

/**
 * 时间线与评论事件 DTO：宽松覆盖 timeline 端点混合项（评论/事件/交叉引用/关联 PR）。
 *
 * - 评论项：event == "commented"，含 body/htmlUrl
 * - 事件项：event == "closed"/"reopened"/"labeled"/"cross-referenced"/"connected"/"linked" 等
 * - 交叉引用：event == "cross-referenced"，target 在 source.issue
 * - 关联 PR：event == "connected"/"linked"，source.issue 为 PR
 */
@Serializable
data class IssueEventDto(
    val id: Long,
    val event: String,
    val actor: UserDto? = null,
    val body: String? = null,
    val htmlUrl: String? = null,
    val createdAt: String? = null,
    val commitId: String? = null,
    val commitUrl: String? = null,
    val label: LabelDto? = null,
    val milestone: MilestoneDto? = null,
    val source: CrossReferenceSourceDto? = null,
)

@Serializable
data class CrossReferenceSourceDto(
    val issue: IssueDto? = null,
)
