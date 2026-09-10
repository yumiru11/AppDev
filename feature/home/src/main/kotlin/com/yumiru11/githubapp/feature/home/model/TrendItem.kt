package com.yumiru11.githubapp.feature.home.model

import androidx.compose.runtime.Immutable

/**
 * Trending 榜单条目（领域模型；DTO → domain 映射见 data/TrendMappers）。
 *
 * 只读 UI 模型，标注 [Immutable]：行组件参数稳定，滚动/刷新时跳过行级重组（#86）。
 *
 * @param fullName `owner/repo`
 * @param url 仓库页链接（点击 → 应用内仓库详情）
 */
@Immutable
data class TrendItem(
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val url: String,
)
