package com.yumiru11.githubapp.feature.repo

/**
 * 仓库文件浏览 UI 状态（T11 文件树 + 代码浏览 + T22 文件编辑提交）。
 *
 * 树 / 文件内容 / 文件编辑三块独立子状态：互不阻塞（树加载失败不影响已打开的查看器，反之亦然）。
 */
data class RepoFilesUiState(
    val treeState: TreeState = TreeState.Loading,
    val selectedPath: String? = null,
    val fileState: FileViewState = FileViewState.Idle,
    val editState: FileEditState = FileEditState.Idle,
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

/** 文件编辑子状态（T22 文件编辑提交，plan.md §7.4）。 */
sealed interface FileEditState {
    /** 未在编辑（树/查看器）。 */
    data object Idle : FileEditState

    /**
     * 编辑中。
     *
     * @param isNew 新建文件模式（路径由提交对话框输入；sha 恒为 null）
     * @param text 编辑器当前全文（编辑器是文本唯一事实源，[RepoFilesViewModel.onEditorTextChanged] 同步）
     * @param sha 被替换文件 blob SHA（非新建；409 覆盖后流转）
     * @param isMarkdown Markdown 文件（编辑区提供预览切换，与查看器 Rendered 态同一渲染管线）
     */
    data class Editing(
        val isNew: Boolean,
        val text: String,
        val sha: String?,
        val isMarkdown: Boolean,
    ) : FileEditState

    /**
     * 写请求进行中（提交/删除/覆盖重试）。
     * 携带编辑快照供界面继续渲染（提交期间编辑器只读展示，不丢文本）。
     */
    data class Submitting(
        val text: String,
        val isNew: Boolean,
        val isMarkdown: Boolean,
    ) : FileEditState

    /**
     * 409 冲突（sha 过期，远端已变更）：用户三选一（重载/覆盖/保留本地），绝不静默覆盖。
     *
     * @param operation 冲突来源操作（UPDATE = 编辑提交；DELETE = 删除）
     * @param latestSha 远端最新 blob SHA（409 响应体 message 解析）
     * @param localText 用户本地文本（UPDATE 覆盖/保留用；DELETE 为 null）
     * @param message 原始提交信息（覆盖重试复用）
     * @param branch 原始目标分支（覆盖重试复用；null = 当前分支）
     * @param isMarkdown 冲突文件是否为 Markdown（覆盖重试提交期间界面渲染）
     */
    data class Conflict(
        val operation: ConflictOperation,
        val latestSha: String,
        val localText: String?,
        val message: String,
        val branch: String?,
        val isMarkdown: Boolean,
    ) : FileEditState
}

/** 冲突来源操作（决定冲突对话框语义与可用选项）。 */
enum class ConflictOperation { UPDATE, DELETE }
