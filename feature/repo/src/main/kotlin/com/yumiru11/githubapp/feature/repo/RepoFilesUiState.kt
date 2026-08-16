package com.yumiru11.githubapp.feature.repo

/**
 * 仓库文件浏览 UI 状态（T11 文件树 + 代码浏览）。
 *
 * 树与文件内容分开成子状态：树加载失败不影响已打开的查看器，反之亦然。
 */
data class RepoFilesUiState(
    val treeState: TreeState = TreeState.Loading,
    val selectedPath: String? = null,
    val fileState: FileViewState = FileViewState.Idle,
)

/** 文件树子状态。 */
sealed interface TreeState {
    /** 加载中（首次进入文件 Tab） */
    data object Loading : TreeState

    /** 加载成功（根节点列表；子目录展开状态在节点上） */
    data class Loaded(
        val rootNodes: List<GitTreeNode>,
    ) : TreeState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: RepoErrorType,
    ) : TreeState
}

/** 文件内容子状态。 */
sealed interface FileViewState {
    /** 未选中文件（树视图） */
    data object Idle : FileViewState

    /** 内容加载中 */
    data object Loading : FileViewState

    /** 内容就绪（含分类结果：CODE/MARKDOWN/BINARY/TOO_LARGE） */
    data class Loaded(
        val data: FileContentData,
    ) : FileViewState

    /** 加载失败 */
    data class Error(
        val errorType: RepoErrorType,
    ) : FileViewState
}
