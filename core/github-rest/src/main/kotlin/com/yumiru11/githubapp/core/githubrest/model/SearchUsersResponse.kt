package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GET /search/users 响应（用户 DTO 复用 [UserDto]）。
 */
@Serializable
data class SearchUsersResponse(
    val totalCount: Int = 0,
    val incompleteResults: Boolean = false,
    val items: List<UserDto> = emptyList(),
)
