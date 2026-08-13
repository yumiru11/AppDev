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
    // T20 追加统计字段（additive，勿改既有字段；GitHub REST /user 无 starred 计数，Starred 走列表）
    val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
)
