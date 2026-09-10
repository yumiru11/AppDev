package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 创建 Release 请求体（POST /repos/{owner}/{repo}/releases，L05）。
 *
 * [targetCommitish] → `target_commitish`：分支名或 commit SHA；未发布（draft）时
 * 决定 Tag 指向哪一个 commit（GitHub 默认默认分支）。
 */
@Serializable
data class CreateReleaseRequest(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("target_commitish")
    val targetCommitish: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)
