package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * Trending 镜像条目 DTO（Unpublished/GithubTrending 的 `trends/trending_{type}-all.json`）。
 *
 * 字段对齐镜像 JSON 的**实际结构**（owner/repo 分列，镜像侧没有 url 字段；
 * 参考实现 gh4a 的 Trend 模型同款），[url] 因此按 GitHub 仓库地址派生。
 * 镜像还带 new_stars/builtBy 等字段，由 ignoreUnknownKeys 容忍。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 */
@Serializable
data class TrendDto(
    val owner: String = "",
    val repo: String = "",
    val description: String? = null,
    val language: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
) {
    /** `owner/repo`；任一侧缺失时为空串（映射侧据此过滤脏数据） */
    val fullName: String get() = if (owner.isBlank() || repo.isBlank()) "" else "$owner/$repo"

    /** 仓库页链接（应用内导航解析基准） */
    val url: String get() = "https://github.com/$fullName"
}
