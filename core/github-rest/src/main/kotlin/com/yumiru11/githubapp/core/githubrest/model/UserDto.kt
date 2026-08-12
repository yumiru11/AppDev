package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST 用户 DTO（GET /user、嵌套 owner 等）。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 * 字段为 API 子集，ignoreUnknownKeys 容忍 GitHub 未来新增字段。
 */
@Serializable
data class UserDto(
    val login: String,
    val id: Long,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String? = null,
    val bio: String? = null,
    val type: String? = null,
)
