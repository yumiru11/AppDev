package com.yumiru11.githubapp.feature.repo

/**
 * 仓库管理操作回调（T12 + L04/L05/L06）。
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
    /** L04：删除仓库（顶部「更多」菜单项；仅 admin 可见） */
    val onDeleteRepository: () -> Unit = {},
    /** L05：新建 Release 表单页（Releases Tab；有写权限时可见） */
    val onCreateRelease: () -> Unit = {},
    /** L05：上传 Release 附件（releaseId / 文件名 / 内容字节） */
    val onUploadAsset: (Long, String, ByteArray) -> Unit = { _, _, _ -> },
)
