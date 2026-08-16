package com.yumiru11.githubapp.core.data.model

/**
 * 搜索命中的 Issue/PR（/search/issues items 的领域模型，feature:issue 的 Issue
 * 为详情页专用模型，搜索只需子集，故独立建模——两模型互不依赖）。
 */
data class SearchIssue(
    val id: Long,
    val number: Int,
    val title: String,
    /** GitHub REST state 字段（"open"/"closed"） */
    val state: String,
    /** issue 搜索同时返回 PR，`pull_request` 字段非 null 即为 PR */
    val isPullRequest: Boolean,
    val authorLogin: String? = null,
    /** 归属仓库 owner/name（repository_url 解析，形如 octocat/Hello-World） */
    val repoFullName: String? = null,
    val htmlUrl: String? = null,
)
