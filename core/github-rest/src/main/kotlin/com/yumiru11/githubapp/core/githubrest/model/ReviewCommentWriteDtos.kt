package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

// PR 行内评论写操作请求 DTO（T16）。纯 Kotlin + kotlinx-serialization
// （架构护栏：model 包禁 android import）；SnakeCase 命名策略见 GitHubRestClient。

/** POST /repos/{owner}/{repo}/pulls/{number}/comments：新增/回复行内评论请求体 */
@Serializable
data class CreateReviewCommentRequest(
    val body: String,
    /** 评论针对的提交 SHA（新增评论必填；回复评论可忽略） */
    val commitId: String? = null,
    /** 文件路径（新增评论必填；回复评论可忽略） */
    val path: String? = null,
    /** diff 内行号：side=RIGHT 用新文件行号，side=LEFT 用旧文件行号（2022-11-28 API） */
    val line: Int? = null,
    /** LEFT = 旧文件侧（删除行），RIGHT = 新文件侧（新增行） */
    val side: String? = null,
    /** 回复目标评论 id（回复模式必填） */
    val inReplyToId: Long? = null,
)

/** PATCH /repos/{owner}/{repo}/pulls/comments/{comment_id}：编辑行内评论请求体 */
@Serializable
data class UpdateReviewCommentRequest(
    val body: String,
)
