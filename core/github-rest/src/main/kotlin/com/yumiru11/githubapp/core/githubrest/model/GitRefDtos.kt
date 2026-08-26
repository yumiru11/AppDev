package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

// Git References DTO（T22 新建分支前置 / T23 分支管理复用）。
// 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。

/** GET /git/ref/heads/{ref} 与 POST /git/refs 的响应体。 */
@Serializable
data class GitRefDto(
    val ref: String,
    val `object`: RefObject,
) {
    /** 引用指向的对象（type = "commit"）。 */
    @Serializable
    data class RefObject(
        val sha: String,
        val type: String,
        val url: String? = null,
    )
}

/** POST /git/refs 请求体：ref 必须为完整形式（refs/heads/{name}），sha 为基提交。 */
@Serializable
data class GitRefCreateRequest(
    val ref: String,
    val sha: String,
)

/** GET /repos/{owner}/{repo}/branches 响应条目（分支列表，T23 分支管理）。 */
@Serializable
data class BranchDto(
    val name: String,
    /** 分支头提交（sha 供展示/校验） */
    val commit: BranchCommitDto? = null,
    /** 受保护分支（GitHub 强制保护规则，不可 force push/删除） */
    val `protected`: Boolean = false,
)

/** 分支头提交引用（GET /branches 的 commit 对象）。 */
@Serializable
data class BranchCommitDto(
    val sha: String? = null,
    val url: String? = null,
)
