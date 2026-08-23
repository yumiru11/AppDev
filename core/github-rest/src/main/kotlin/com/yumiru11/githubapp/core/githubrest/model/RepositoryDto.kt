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
    /** 当前会话的仓库权限（T17：Review/Merge 写入口显隐；缺失 = 游客/未知 → 保守隐藏） */
    val permissions: RepositoryPermissionsDto? = null,
)

/** 仓库权限位（GET /repos/{owner}/{repo} 的 permissions 对象；按当前会话计算） */
@Serializable
data class RepositoryPermissionsDto(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false,
)
