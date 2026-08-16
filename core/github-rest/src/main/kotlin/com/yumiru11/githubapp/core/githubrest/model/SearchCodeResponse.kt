package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GET /search/code 响应。
 *
 * 代码搜索有额外限制（plan.md §9.3）：必须登录（anonymous 401）、
 * 仅索引默认分支、每仓库结果截断（incompleteResults）——详见 SearchApi 注释。
 */
@Serializable
data class SearchCodeResponse(
    val totalCount: Int = 0,
    val incompleteResults: Boolean = false,
    val items: List<CodeSearchItemDto> = emptyList(),
)
