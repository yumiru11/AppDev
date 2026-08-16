package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * 代码搜索结果条目（GET /search/code items）。
 *
 * [repository] 为完整仓库对象（full_name 供 UI 展示归属仓库）。
 */
@Serializable
data class CodeSearchItemDto(
    val name: String,
    val path: String,
    val sha: String? = null,
    val htmlUrl: String? = null,
    val repository: RepositoryDto? = null,
)
