package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST Pull Request DTO 集合（T15）。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 * 字段为 API 子集：GitHubRestClient.createJson() 已配 SnakeCase 命名策略
 * （snake_case JSON ↔ camelCase 属性自动映射）+ ignoreUnknownKeys 容忍新增字段。
 *
 * 端点对应：
 * - GET /repos/{owner}/{repo}/pulls — 列表
 * - GET /repos/{owner}/{repo}/pulls/{number} — 详情
 * - GET /repos/{owner}/{repo}/pulls/{number}/commits — 提交列表
 * - GET /repos/{owner}/{repo}/pulls/{number}/files — 文件列表
 * - GET /repos/{owner}/{repo}/commits/{ref}/check-runs — Checks
 * - GET /repos/{owner}/{repo}/commits/{ref}/status — 合并状态摘要
 */
@Serializable
data class PullRequestDto(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    val user: UserDto? = null,
    val labels: List<LabelDto> = emptyList(),
    val assignees: List<UserDto> = emptyList(),
    val milestone: MilestoneDto? = null,
    val comments: Int = 0,
    val reviewComments: Int = 0,
    val commits: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFiles: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val closedAt: String? = null,
    val mergedAt: String? = null,
    val htmlUrl: String? = null,
    /** 可合并性：true/false；null = GitHub 尚未完成合并性检查（待检查） */
    val mergeable: Boolean? = null,
    /** 合并状态明细（clean/dirty/blocked/behind/unstable/draft/unknown） */
    val mergeableState: String? = null,
    /** 草稿 PR（GitHub 网页 Draft 徽标） */
    val draft: Boolean = false,
    val head: PullRequestBranchDto? = null,
    val base: PullRequestBranchDto? = null,
    val requestedReviewers: List<UserDto> = emptyList(),
    val requestedTeams: List<TeamDto> = emptyList(),
)

/** PR 分支信息（head/base）：label 形如 "owner:branch"，ref 为分支名，sha 为提交哈希 */
@Serializable
data class PullRequestBranchDto(
    val label: String? = null,
    val ref: String? = null,
    val sha: String? = null,
    val repo: PullRequestRepoDto? = null,
)

/** 分支归属仓库（API 子集：展示用 name/fullName） */
@Serializable
data class PullRequestRepoDto(
    val name: String? = null,
    val fullName: String? = null,
)

/** 请求审查的团队（API 子集） */
@Serializable
data class TeamDto(
    val name: String? = null,
    val slug: String? = null,
)

/** PR 提交条目（GET /pulls/{number}/commits） */
@Serializable
data class PullRequestCommitDto(
    val sha: String,
    val commit: PullRequestCommitDetailDto? = null,
    val author: UserDto? = null,
    val committer: UserDto? = null,
    val htmlUrl: String? = null,
    /** 提交涉及文件（该端点默认返回；展开 diff 摘要用） */
    val files: List<PullRequestCommitFileDto> = emptyList(),
)

/** 提交元数据（message/author/committer 时间） */
@Serializable
data class PullRequestCommitDetailDto(
    val message: String? = null,
    val author: CommitAuthorDto? = null,
    val committer: CommitAuthorDto? = null,
)

/** 提交作者/提交者（name/email/date） */
@Serializable
data class CommitAuthorDto(
    val name: String? = null,
    val email: String? = null,
    val date: String? = null,
)

/** 提交内文件变更摘要（+N −M） */
@Serializable
data class PullRequestCommitFileDto(
    val filename: String? = null,
    val status: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
)

/** PR 文件变更条目（GET /pulls/{number}/files） */
@Serializable
data class PullRequestFileDto(
    val filename: String,
    val status: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    /** unified diff 文本（展开查看用；T16 完整 DiffView 前先展示原文） */
    val patch: String? = null,
    val rawUrl: String? = null,
    val blobUrl: String? = null,
)

/** PR Review 条目（GET /pulls/{number}/reviews） */
@Serializable
data class PullRequestReviewDto(
    val id: Long,
    val user: UserDto? = null,
    val body: String? = null,
    /** APPROVED / CHANGES_REQUESTED / COMMENTED / DISMISSED */
    val state: String? = null,
    val submittedAt: String? = null,
    val commitId: String? = null,
    val htmlUrl: String? = null,
)

/** PR 行内评论条目（GET /pulls/{number}/comments） */
@Serializable
data class PullRequestReviewCommentDto(
    val id: Long,
    val user: UserDto? = null,
    val body: String? = null,
    val path: String? = null,
    val line: Int? = null,
    val position: Int? = null,
    val createdAt: String? = null,
    val htmlUrl: String? = null,
    val inReplyToId: Long? = null,
)

/** Check Run 条目（GET /commits/{ref}/check-runs） */
@Serializable
data class CheckRunDto(
    val id: Long,
    val name: String? = null,
    /** queued / in_progress / completed */
    val status: String? = null,
    /** success / failure / neutral / cancelled / skipped / timed_out / action_required */
    val conclusion: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val output: CheckRunOutputDto? = null,
    val app: CheckRunAppDto? = null,
    val htmlUrl: String? = null,
    val detailsUrl: String? = null,
)

/** Check Run 输出（失败详情展开用） */
@Serializable
data class CheckRunOutputDto(
    val title: String? = null,
    val summary: String? = null,
    val text: String? = null,
)

/** Check Run 所属应用（GitHub Actions 等） */
@Serializable
data class CheckRunAppDto(
    val name: String? = null,
)

/** Check Runs 响应包装（total_count + check_runs） */
@Serializable
data class CheckRunsResponseDto(
    val totalCount: Int = 0,
    val checkRuns: List<CheckRunDto> = emptyList(),
)

/** 合并状态摘要（GET /commits/{ref}/status）：state = success/failure/pending */
@Serializable
data class CombinedStatusDto(
    val state: String? = null,
    val totalCount: Int = 0,
    val statuses: List<CombinedStatusItemDto> = emptyList(),
)

/** 单个状态条目（context 为 CI 名称） */
@Serializable
data class CombinedStatusItemDto(
    val state: String? = null,
    val context: String? = null,
    val description: String? = null,
    val targetUrl: String? = null,
    val createdAt: String? = null,
)
