package com.yumiru11.githubapp.feature.repo

/**
 * COMMIT 详情页 UI 状态（L09）。
 *
 * 文件变更列表与 diff 同页两态：选中文件（[Success.selectedFile] 非空）显示该文件 unified diff，
 * 否则显示提交头 + 文件列表（不进导航，与 Release 详情展开同款交互）。
 */
sealed interface CommitDetailUiState {
    /** 加载中 */
    data object Loading : CommitDetailUiState

    /** 加载成功 */
    data class Success(
        val commit: CommitDetail,
        /** 正在查看 diff 的文件（null = 文件列表态） */
        val selectedFile: CommitFile? = null,
        /** 选中文件的 diff 行（[CommitDiffParser] 解析结果；无 patch 时为空） */
        val diffLines: List<CommitDiffLine> = emptyList(),
    ) : CommitDetailUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射，ViewModel 不产英文） */
    data class Error(
        val errorType: RepoErrorType,
    ) : CommitDetailUiState
}
