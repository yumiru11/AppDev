package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST Tag DTO（GET /repos/{owner}/{repo}/tags）。
 */
@Serializable
data class TagDto(
    val name: String,
    val commit: TagCommitDto,
)

/**
 * Tag 指向的 commit（Git Data API 引用）。
 */
@Serializable
data class TagCommitDto(
    val sha: String,
    val url: String? = null,
)
