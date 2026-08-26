package com.yumiru11.githubapp.feature.repo

/** 分支条目（T23 分支管理，GET /repos/{o}/{r}/branches）。 */
data class Branch(
    val name: String,
    /** 分支头提交 SHA */
    val sha: String? = null,
    /** 受保护分支（GitHub 强制保护规则：不可 force push/删除） */
    val isProtected: Boolean = false,
)

/** 仓库写控制上下文（T23：新建/删除分支显隐；加载失败 → 保守隐藏写入口）。 */
data class BranchControl(
    val canPush: Boolean = false,
    val defaultBranch: String? = null,
)

/** 分支管理页 UI 状态（T23）。 */
sealed interface BranchesUiState {
    /** 加载中 */
    data object Loading : BranchesUiState

    /**
     * 加载成功。
     *
     * @param branches 分支列表（默认分支已排首）
     * @param canPush 当前会话是否有推送权限（false 时隐藏新建/删除入口）
     * @param isBusy 新建/删除进行中（防重入）
     */
    data class Success(
        val branches: List<Branch>,
        val defaultBranch: String?,
        val canPush: Boolean = false,
        val isBusy: Boolean = false,
    ) : BranchesUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: RepoErrorType,
    ) : BranchesUiState
}

/** 分支管理事件（Snackbar 文案由 UI 层映射，ViewModel 不产文案）。 */
sealed interface BranchEvent {
    data class Created(
        val name: String,
    ) : BranchEvent

    data class Deleted(
        val name: String,
    ) : BranchEvent

    data class Failed(
        val errorType: RepoErrorType,
    ) : BranchEvent
}
