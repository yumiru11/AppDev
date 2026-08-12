package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST 仓库 DTO（GET /repos/{owner}/{repo}）。
 *
 * `private` 为 Kotlin 保留词，用 @SerialName 映射到 isPrivate。
 */
@Serializable
data class RepositoryDto(
    val id: Long,
    val name: String,
    val fullName: String,
    @SerialName("private")
    val isPrivate: Boolean,
    val owner: UserDto,
    val description: String? = null,
    val htmlUrl: String? = null,
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val language: String? = null,
    val defaultBranch: String? = null,
)
