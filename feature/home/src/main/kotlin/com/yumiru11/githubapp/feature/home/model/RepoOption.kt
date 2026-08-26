package com.yumiru11.githubapp.feature.home.model

/**
 * 仓库选择项（#89 长条按钮的 {owner}/{repo} 上下文来源）。
 * 只保留选择器展示与路由所需的最小字段；[fullName] 兼作 LazyColumn 稳定 key。
 */
data class RepoOption(
    val owner: String,
    val name: String,
    val description: String?,
    val isPrivate: Boolean,
) {
    val fullName: String get() = "$owner/$name"
}
