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
