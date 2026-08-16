package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GET /search/repositories 响应（搜索 REST-only，plan.md §3.2）。
 *
 * - [totalCount]：命中总数（UI 目前不展示，保留供后续）
 * - [incompleteResults]：服务端截断标志
 * - [items]：本页结果（仓库 DTO 复用 [RepositoryDto]）
 */
@Serializable
data class SearchRepositoriesResponse(
    val totalCount: Int = 0,
    val incompleteResults: Boolean = false,
    val items: List<RepositoryDto> = emptyList(),
)
