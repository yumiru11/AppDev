package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType

/** 分支选项（T23 创建 PR 的 base/head 候选）。 */
data class RepositoryBranch(
    val name: String,
    val isDefault: Boolean = false,
)

/**
 * 创建 PR 页 UI 状态（T23）。
 *
 * [Form] 持有表单字段（标题/描述/base/head），提交按钮可用性由 UI 派生：
 * 标题非空 && head != base && 有推送权限 && 非提交中。
 */
sealed interface PullRequestCreateUiState {
    /** 加载中（分支列表 + 仓库控制） */
    data object Loading : PullRequestCreateUiState

    /**
     * 表单就绪。
     *
     * @param canCreate 当前会话是否有推送权限（false 时提交禁用并提示）
     * @param isSubmitting 提交进行中（防重入）
     * @param title/body/baseBranch/headBranch 表单字段
     */
    data class Form(
        val branches: List<RepositoryBranch>,
        val canCreate: Boolean = false,
        val isSubmitting: Boolean = false,
        val title: String = "",
        val body: String = "",
        val baseBranch: String = "",
        val headBranch: String = "",
    ) : PullRequestCreateUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: PullRequestErrorType,
    ) : PullRequestCreateUiState
}

/** 创建 PR 事件（成功 → 宿主导航打开新 PR；失败 → Snackbar）。 */
sealed interface PullRequestCreateEvent {
    data class Created(
        val number: Int,
    ) : PullRequestCreateEvent

    data class Failed(
        val errorType: PullRequestErrorType,
    ) : PullRequestCreateEvent
}
