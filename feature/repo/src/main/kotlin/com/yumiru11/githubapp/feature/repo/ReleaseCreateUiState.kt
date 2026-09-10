package com.yumiru11.githubapp.feature.repo

/**
 * 新建 Release 表单 UI 状态（L05）。
 *
 * @param tagName Tag 名（必填；GitHub 允许 "v1.0.0" 或已存在的分支名）
 * @param targetCommitish 目标分支/commit（空 → 用仓库默认分支，由 UI 预填）
 * @param draft 草稿（不公开可见）
 * @param prerelease 预发布标记
 * @param created 创建成功的 Tag（非空 → UI 导航回仓库详情并刷新 Releases）
 */
data class ReleaseCreateUiState(
    val tagName: String = "",
    val targetCommitish: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val isSubmitting: Boolean = false,
    val tagError: Boolean = false,
    val created: String? = null,
) {
    /** 提交按钮可用性：Tag 非空且无进行中的提交 */
    val canSubmit: Boolean
        get() = !isSubmitting && tagName.isNotBlank()
}

/**
 * 新建 Release 失败事件（UI 层 stringResource 映射文案）。
 */
sealed interface ReleaseCreateEvent {
    /** 422：Tag 非法或已存在 */
    data object ValidationFailed : ReleaseCreateEvent

    /** 403：无写权限 */
    data object PermissionDenied : ReleaseCreateEvent

    /** 网络/未知错误 */
    data object Failed : ReleaseCreateEvent
}
