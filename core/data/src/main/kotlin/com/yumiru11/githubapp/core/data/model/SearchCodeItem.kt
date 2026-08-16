package com.yumiru11.githubapp.core.data.model

/**
 * 代码搜索结果条目（/search/code items 的领域模型）。
 */
data class SearchCodeItem(
    /** 文件名 */
    val name: String,
    /** 仓库内路径 */
    val path: String,
    /** 归属仓库 owner/name */
    val repoFullName: String,
    val htmlUrl: String? = null,
)
