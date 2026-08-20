package com.yumiru11.githubapp.feature.repo

/**
 * 仓库管理操作回调（T12）。
 *
 * 与 [com.yumiru11.githubapp.core.ui.RepoDetailActions] 同模式：回调分组避免
 * RepoDetailContent 参数爆炸（detekt LongParameterList），Composable 保持可测试性。
 */
data class RepoManagementCallbacks(
    val onToggleStar: () -> Unit,
    val onToggleWatch: () -> Unit,
    val onFork: () -> Unit,
    val onEnsureReleasesLoaded: () -> Unit,
    val onEnsureTagsLoaded: () -> Unit,
    val onReleaseClick: (Long) -> Unit,
    val onCollapseRelease: () -> Unit,
)
