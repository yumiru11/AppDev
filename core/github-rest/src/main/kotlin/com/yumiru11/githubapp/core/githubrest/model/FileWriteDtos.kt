package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

// 文件写操作请求/响应 DTO 集合（T22 文件编辑提交，plan.md §7.4）。
// 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）；
// 请求体经 SnakeCase 命名策略序列化。

/** PUT /repos/{owner}/{repo}/contents/{path}：创建/更新文件请求体。 */
@Serializable
data class FileWriteRequest(
    /** 提交信息（必填）。 */
    val message: String,
    /** 新文件内容（Base64 编码，必填）。 */
    val content: String,
    /** 被替换文件的 blob SHA；null = 新建文件（T22 新建模式）。 */
    val sha: String? = null,
    /** 目标分支名；null = 仓库默认分支。分支必须已存在（新建分支需先经 Git Refs API 创建）。 */
    val branch: String? = null,
)

/** DELETE /repos/{owner}/{repo}/contents/{path}：删除文件请求体（参数经 JSON body，官方 curl 同款）。 */
@Serializable
data class FileDeleteRequest(
    /** 提交信息（必填）。 */
    val message: String,
    /** 被删除文件的 blob SHA（必填）。 */
    val sha: String,
    /** 目标分支名；null = 仓库默认分支。 */
    val branch: String? = null,
)

/** 写操作响应（T22）：content.sha = 新 blob SHA；commit.sha = 新提交 SHA。 */
@Serializable
data class ContentWriteResponseDto(
    val content: ContentWriteItemDto? = null,
    val commit: CommitWriteDto? = null,
) {
    /** 文件条目子对象（仅需要 sha/path）。 */
    @Serializable
    data class ContentWriteItemDto(
        val sha: String? = null,
        val path: String? = null,
    )

    /** 提交子对象（仅需要 sha；commit.html_url 供后续跳转可扩展）。 */
    @Serializable
    data class CommitWriteDto(
        val sha: String? = null,
    )
}
