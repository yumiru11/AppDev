package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/** 创建 PR 请求体（POST /repos/{owner}/{repo}/pulls，T23）。head/base 为分支名。 */
@Serializable
data class CreatePullRequestRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null,
    val draft: Boolean = false,
)
