package com.yumiru11.githubapp.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * 仓库详情页导航动作（AppNavHost 注入，feature:repo 经 [LocalRepoDetailActions] 消费）。
 *
 * - [onNavigateToParsedUrl]：内部链接 → 应用内导航（[navigateToParsedUrl]）
 * - [onOpenExternal]：外部链接 → Chrome Custom Tabs
 * - [onEditMarkdown]：Markdown 文件编辑入口（T21；null = 未接线，文件查看器隐藏编辑按钮）
 */
data class RepoDetailActions(
    val onNavigateToParsedUrl: (ParsedUrl) -> Unit = {},
    val onOpenExternal: (String) -> Unit = {},
    val onEditMarkdown: ((String) -> Unit)? = null,
)

/** 仓库详情页导航动作的 CompositionLocal（AppNavHost 提供，feature:repo 读取） */
val LocalRepoDetailActions = staticCompositionLocalOf { RepoDetailActions() }
