package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GET /search/issues 响应。
 *
 * GitHub 的 issue 搜索同时返回 Issue 与 PR（PR 条目带 `pull_request` 字段，
 * [IssueDto.pullRequest] 非 null 即 PR）——Issue/PR 两个 Tab 共用此端点，
 * PR Tab 由仓库层追加 `is:pr` qualifier 过滤。
 */
@Serializable
data class SearchIssuesResponse(
    val totalCount: Int = 0,
    val incompleteResults: Boolean = false,
    val items: List<IssueDto> = emptyList(),
)
