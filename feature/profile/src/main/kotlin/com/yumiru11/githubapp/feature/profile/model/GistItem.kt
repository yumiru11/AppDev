package com.yumiru11.githubapp.feature.profile.model

import androidx.compose.runtime.Immutable

/**
 * Gist 列表条目（L11 领域模型；DTO → domain 映射见 GistMappers）。
 *
 * 只读 UI 模型，标注 [Immutable]：行组件参数稳定，滚动/翻页时跳过行级重组（#86）。
 *
 * @param fileName 首个文件名（GitHub 以文件名为 files 对象的键；无文件时回退 id）
 * @param htmlUrl gist 页链接（v1 经 Chrome Custom Tabs 打开，不做详情渲染）
 */
@Immutable
data class GistItem(
    val id: String,
    val fileName: String,
    val language: String?,
    val description: String?,
    val createdAt: String?,
    val htmlUrl: String?,
)
