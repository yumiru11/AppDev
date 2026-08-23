package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PR Review / Merge / Update branch 写请求与响应 DTO（T17）。
 *
 * 端点对应：
 * - POST /repos/{owner}/{repo}/pulls/{number}/reviews
 * - PUT /repos/{owner}/{repo}/pulls/{number}/merge
 * - PUT /repos/{owner}/{repo}/pulls/{number}/update-branch
 *
 * 与 PullRequestDto 一致：纯 Kotlin + kotlinx-serialization（model 包禁 android import）；
 * GitHubRestClient.createJson() 的 SnakeCase 策略 + @SerialName 显式映射写请求字段。
 */
@Serializable
data class CreateReviewRequest(
    /** Review 正文（COMMENT 结论必填；APPROVE/REQUEST_CHANGES 可选；空串不发 → null） */
    val body: String? = null,
    /** 结论：APPROVE / REQUEST_CHANGES / COMMENT（GitHub REST event 字段） */
    val event: String,
    /** 审查对应的提交 SHA（默认 PR head） */
    @SerialName("commit_id")
    val commitId: String? = null,
)

/** Merge 请求（PUT /pulls/{number}/merge） */
@Serializable
data class MergePullRequestRequest(
    /** 合并提交标题（默认 PR 标题） */
    @SerialName("commit_title")
    val commitTitle: String? = null,
    /** 合并提交详细说明（可选） */
    @SerialName("commit_message")
    val commitMessage: String? = null,
    /** PR head sha 校验（防并发合并；不匹配 → 409） */
    val sha: String? = null,
    /** merge / squash / rebase */
    @SerialName("merge_method")
    val mergeMethod: String? = null,
)

/** Merge 响应（200/201：{sha, merged, message}） */
@Serializable
data class MergePullRequestResult(
    val sha: String? = null,
    val merged: Boolean = false,
    val message: String? = null,
)

/** Update branch 请求（PUT /pulls/{number}/update-branch；仅同仓库 PR） */
@Serializable
data class UpdateBranchRequest(
    /** 期望的 head sha（可选；提供则 GitHub 校验当前 head 一致后更新） */
    @SerialName("expected_head_sha")
    val expectedHeadSha: String? = null,
)

/** Update branch 响应（202 Accepted：{message, url}） */
@Serializable
data class UpdateBranchResult(
    val message: String? = null,
    val url: String? = null,
)
