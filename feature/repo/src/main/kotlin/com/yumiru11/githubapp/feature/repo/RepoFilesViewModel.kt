@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底（同 RepoDetailViewModel 先例）

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 仓库文件浏览 ViewModel（T11 文件树 + 代码浏览 + T22 文件编辑提交）。
 *
 * - 根树：首次进入文件 Tab 加载（ref = 默认分支，UI 传入；同 ref 不重复加载）
 * - 目录：点击展开 → 首次按需取子树（[GitTreeNode.children] == null），
 *   失败保持收起（保留重试机会）；再次点击收起
 * - 文件：点击 → 加载内容（分类判定在 RepoRepository，本层只透传状态）
 * - 编辑（T22）：[startEdit]/[startNewFile] 进入编辑态 → [commitEdit] 提交
 *   （当前分支或新建分支；新建分支经 Git Refs API 先建引用，失败回编辑态并上抛
 *   [FileEditEvent.Failed]）→ 409 冲突转 [FileEditState.Conflict]，
 *   三选项（[reloadAfterConflict]/[overwriteAfterConflict]/[keepLocalAfterConflict]）绝不静默覆盖；
 *   [deleteFile] 删除（确认由 UI 弹窗）；提交/删除成功后清缓存并重载目标分支树（AC4 缓存失效）。
 *
 * 错误一律映射为 [RepoErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 * 编辑流程事件（Snackbar/剪贴板）经 [editEvents] 上抛，UI 层消费。
 */
@HiltViewModel
class RepoFilesViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repoRepository: RepoRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        /** 深链（BLOB 路由）进入时的 ref 参数；树内流程以 [loadedRef] 为准 */
        private val refArg: String = savedStateHandle["ref"] ?: "main"

        private val _uiState = MutableStateFlow(RepoFilesUiState())
        val uiState: StateFlow<RepoFilesUiState> = _uiState.asStateFlow()

        /** 编辑流程事件通道（UI 层：Snackbar 文案 / 剪贴板复制 / 树刷新驱动）。 */
        private val _editEvents = Channel<FileEditEvent>(Channel.BUFFERED)
        val editEvents: Flow<FileEditEvent> = _editEvents.receiveAsFlow()

        /** 已加载的根树分支（同 ref 免重复拉取；Tab 切换重建组合不触发网络） */
        private var loadedRef: String? = null

        fun loadRootTree(ref: String) {
            if (loadedRef == ref && _uiState.value.treeState is TreeState.Loaded) return
            loadedRef = ref
            // T23：分支 Chip 显示当前查看分支（分支切换返回后经此回写）
            _uiState.update { it.copy(currentRef = ref) }
            viewModelScope.launch {
                _uiState.update { it.copy(treeState = TreeState.Loading) }
                repoRepository.getTree(owner, repo, ref).fold(
                    onSuccess = { nodes ->
                        _uiState.update { it.copy(treeState = TreeState.Loaded(nodes)) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(treeState = TreeState.Error(mapError(e))) }
                    },
                )
            }
        }

        fun toggleDirectory(node: GitTreeNode) {
            val state = _uiState.value
            if (state.treeState !is TreeState.Loaded || !node.isDirectory) return

            if (node.isExpanded) {
                // 收起：只更新标记，子节点保留缓存（再次展开免网络）
                _uiState.update {
                    it.copy(
                        treeState =
                            updateTree { roots ->
                                FileTreeBuilder.updateNode(roots, node.path) { n -> n.copy(isExpanded = false) }
                            },
                    )
                }
            } else {
                val children = node.children
                if (children != null) {
                    _uiState.update {
                        it.copy(
                            treeState =
                                updateTree { roots ->
                                    FileTreeBuilder.updateNode(roots, node.path) { n -> n.copy(isExpanded = true) }
                                },
                        )
                    }
                } else {
                    viewModelScope.launch {
                        repoRepository.getChildTree(owner, repo, node.sha, node.path).fold(
                            onSuccess = { childNodes ->
                                _uiState.update {
                                    it.copy(
                                        treeState =
                                            updateTree { roots ->
                                                FileTreeBuilder.updateNode(roots, node.path) { n ->
                                                    n.copy(children = childNodes, isExpanded = true)
                                                }
                                            },
                                    )
                                }
                            },
                            onFailure = {
                                // 子树加载失败：保持收起（用户可重试点击），不阻塞其他操作
                            },
                        )
                    }
                }
            }
        }

        fun openFile(
            node: GitTreeNode,
            ref: String,
        ) {
            if (node.isDirectory || _uiState.value.fileState is FileViewState.Loading) return
            _uiState.update { it.copy(selectedPath = node.path, fileState = FileViewState.Loading) }
            viewModelScope.launch {
                repoRepository.getFileContent(owner, repo, node.path, ref).fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(fileState = FileViewState.Loaded(data)) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(fileState = FileViewState.Error(mapError(e))) }
                    },
                )
            }
        }

        fun retryLoadFile(ref: String) {
            val path = _uiState.value.selectedPath ?: return
            _uiState.update { it.copy(fileState = FileViewState.Loading) }
            viewModelScope.launch {
                repoRepository.getFileContent(owner, repo, path, ref).fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(fileState = FileViewState.Loaded(data)) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(fileState = FileViewState.Error(mapError(e))) }
                    },
                )
            }
        }

        /**
         * BLOB 深链进入：按原始 path 直接加载文件（不经文件树）。
         * ref 优先取路由参数，其次当前已加载分支。
         */
        fun openDeepLinkFile(path: String) {
            if (_uiState.value.fileState is FileViewState.Loading) return
            _uiState.update { it.copy(selectedPath = path, fileState = FileViewState.Loading) }
            viewModelScope.launch {
                repoRepository.getFileContent(owner, repo, path, loadedRef ?: refArg).fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(fileState = FileViewState.Loaded(data)) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(fileState = FileViewState.Error(mapError(e))) }
                    },
                )
            }
        }

        fun closeFile() {
            _uiState.update { it.copy(selectedPath = null, fileState = FileViewState.Idle) }
        }

        // ─── T22 文件编辑提交 ─────────────────────────────────────────────────

        /** 当前查看的文件进入编辑模式（查看器「编辑」按钮；仅文本文件 CODE/MARKDOWN）。 */
        fun startEdit() {
            val data = (_uiState.value.fileState as? FileViewState.Loaded)?.data ?: return
            if (data.kind != FileKind.CODE && data.kind != FileKind.MARKDOWN) return
            _uiState.update {
                it.copy(
                    editState =
                        FileEditState.Editing(
                            isNew = false,
                            text = data.text.orEmpty(),
                            sha = data.sha,
                            isMarkdown = data.kind == FileKind.MARKDOWN,
                        ),
                )
            }
        }

        /** 新建文件模式（文件 Tab「新建文件」按钮；路径由提交对话框输入）。 */
        fun startNewFile() {
            _uiState.update {
                it.copy(
                    selectedPath = null,
                    fileState = FileViewState.Idle,
                    editState = FileEditState.Editing(isNew = true, text = "", sha = null, isMarkdown = false),
                )
            }
        }

        /** 编辑器文本变更同步（编辑器是文本唯一事实源；提交/预览用 [FileEditState.Editing.text]）。 */
        fun onEditorTextChanged(text: String) {
            val current = _uiState.value.editState as? FileEditState.Editing ?: return
            _uiState.update { it.copy(editState = current.copy(text = text)) }
        }

        /** 关闭编辑（返回查看器/树）。编辑内容不保留（保留走 409「保留本地」剪贴板路径）。 */
        fun dismissEdit() {
            _uiState.update { it.copy(editState = FileEditState.Idle) }
        }

        /**
         * 提交编辑/新建（提交对话框确认）。
         *
         * @param message 提交信息（必填；UI 校验，本层兜底放行校验场景）
         * @param newBranchName 新建分支名（null = 提交到当前查看分支；GitHub 自动创建不存在的分支）
         * @param newFilePath 新建文件模式的路径（非新建忽略；路径非空校验 UI 层做）
         */
        fun commitEdit(
            message: String,
            newBranchName: String?,
            newFilePath: String?,
        ) {
            val editing = _uiState.value.editState as? FileEditState.Editing ?: return
            val path = if (editing.isNew) newFilePath?.trim() else _uiState.value.selectedPath
            if (path.isNullOrBlank() || message.isBlank()) return
            val targetBranch = newBranchName?.trim()?.takeIf { it.isNotBlank() } ?: loadedRef
            val isNewBranch = newBranchName?.isNotBlank() == true
            _uiState.update {
                it.copy(editState = FileEditState.Submitting(editing.text, editing.isNew, editing.isMarkdown))
            }
            viewModelScope.launch {
                // 新建分支：Contents API 对不存在的 ref 返回 404（此前误报「仓库未找到」），
                // 必须先经 Git Refs API 从当前分支建引用，再 PUT 文件
                val newBranchTrimmed = newBranchName?.trim()?.takeIf { it.isNotBlank() }
                if (isNewBranch && newBranchTrimmed != null) {
                    val fromBranch = loadedRef ?: refArg
                    repoRepository.createBranch(owner, repo, newBranchTrimmed, fromBranch).fold(
                        onSuccess = {},
                        onFailure = { e ->
                            // 失败回编辑态（文本保留），错误事件上抛——与既有失败路径一致
                            _uiState.update { it.copy(editState = editing) }
                            _editEvents.trySend(FileEditEvent.Failed(mapError(e)))
                            return@launch
                        },
                    )
                }
                repoRepository.updateFileContent(owner, repo, path, editing.text, editing.sha, message, targetBranch).fold(
                    onSuccess = { result ->
                        when (result) {
                            is FileCommitResult.Success -> {
                                _editEvents.trySend(FileEditEvent.Committed(path, targetBranch, isNewBranch))
                                finishEditAndRefresh(targetBranch)
                            }

                            is FileCommitResult.Conflict -> {
                                _uiState.update {
                                    it.copy(
                                        editState =
                                            FileEditState.Conflict(
                                                operation = ConflictOperation.UPDATE,
                                                latestSha = result.latestSha,
                                                localText = editing.text,
                                                message = message,
                                                branch = targetBranch,
                                                isMarkdown = editing.isMarkdown,
                                            ),
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        _editEvents.trySend(FileEditEvent.Failed(mapError(e)))
                        _uiState.update { it.copy(editState = editing) }
                    },
                )
            }
        }

        /**
         * 删除当前文件（删除确认对话框）。
         *
         * @param message 提交信息（必填）
         */
        fun deleteFile(message: String) {
            val editing = _uiState.value.editState as? FileEditState.Editing ?: return
            val path = _uiState.value.selectedPath
            val sha = editing.sha
            // 校验拆两条件（detekt ReturnCount/ComplexCondition 双限）
            if (editing.isNew || path == null || sha == null) return
            if (message.isBlank()) return
            _uiState.update {
                it.copy(editState = FileEditState.Submitting(editing.text, editing.isNew, editing.isMarkdown))
            }
            viewModelScope.launch {
                repoRepository.deleteFile(owner, repo, path, sha, message, loadedRef).fold(
                    onSuccess = { result ->
                        when (result) {
                            is FileCommitResult.Success -> {
                                _editEvents.trySend(FileEditEvent.Deleted(path))
                                finishEditAndRefresh(loadedRef)
                            }

                            is FileCommitResult.Conflict -> {
                                _uiState.update {
                                    it.copy(
                                        editState =
                                            FileEditState.Conflict(
                                                operation = ConflictOperation.DELETE,
                                                latestSha = result.latestSha,
                                                // 删除冲突无「保留本地」；localText 保留界面显示快照（删除重试期间渲染）
                                                localText = editing.text,
                                                message = message,
                                                branch = loadedRef,
                                                isMarkdown = editing.isMarkdown,
                                            ),
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        _editEvents.trySend(FileEditEvent.Failed(mapError(e)))
                        _uiState.update { it.copy(editState = editing) }
                    },
                )
            }
        }

        /**
         * 409「重载」：拉取远端最新内容替换编辑器文本（编辑态继续）。
         * 删除冲突的「重载」：关闭编辑器回查看器并刷新展示最新文件。
         */
        fun reloadAfterConflict() {
            val conflict = _uiState.value.editState as? FileEditState.Conflict ?: return
            val path = _uiState.value.selectedPath ?: return
            if (conflict.operation == ConflictOperation.DELETE) {
                _uiState.update { it.copy(editState = FileEditState.Idle) }
                refreshViewerContent(path)
                return
            }
            viewModelScope.launch {
                repoRepository.getFileContent(owner, repo, path, conflict.branch ?: loadedRef).fold(
                    onSuccess = { data ->
                        _uiState.update {
                            it.copy(
                                fileState = FileViewState.Loaded(data),
                                editState =
                                    FileEditState.Editing(
                                        isNew = false,
                                        text = data.text.orEmpty(),
                                        sha = data.sha,
                                        isMarkdown = data.kind == FileKind.MARKDOWN,
                                    ),
                            )
                        }
                    },
                    onFailure = { e ->
                        // 重载失败：关闭编辑器（避免卡在冲突态），查看器保留旧内容，错误 Snackbar 提示
                        _editEvents.trySend(FileEditEvent.Failed(mapError(e)))
                        _uiState.update { it.copy(editState = FileEditState.Idle) }
                    },
                )
            }
        }

        /**
         * 409「覆盖」（显式选择，绝不静默）：用远端最新 sha 重交本地文本。
         * 删除冲突的「覆盖」：用最新 sha 重试删除。
         */
        fun overwriteAfterConflict() {
            val conflict = _uiState.value.editState as? FileEditState.Conflict ?: return
            val path = _uiState.value.selectedPath ?: return
            if (conflict.operation == ConflictOperation.DELETE) {
                _uiState.update {
                    it.copy(editState = FileEditState.Submitting(conflict.localText.orEmpty(), false, conflict.isMarkdown))
                }
                viewModelScope.launch {
                    repoRepository.deleteFile(owner, repo, path, conflict.latestSha, conflict.message, conflict.branch).fold(
                        onSuccess = { result ->
                            when (result) {
                                is FileCommitResult.Success -> {
                                    _editEvents.trySend(FileEditEvent.Deleted(path))
                                    finishEditAndRefresh(conflict.branch)
                                }

                                is FileCommitResult.Conflict -> {
                                    _uiState.update { it.copy(editState = conflict.copy(latestSha = result.latestSha)) }
                                }
                            }
                        },
                        onFailure = { e ->
                            _editEvents.trySend(FileEditEvent.Failed(mapError(e)))
                            _uiState.update { it.copy(editState = conflict) }
                        },
                    )
                }
                return
            }
            val localText = conflict.localText ?: return
            _uiState.update {
                it.copy(editState = FileEditState.Submitting(localText, false, conflict.isMarkdown))
            }
            viewModelScope.launch {
                repoRepository.updateFileContent(owner, repo, path, localText, conflict.latestSha, conflict.message, conflict.branch).fold(
                    onSuccess = { result ->
                        when (result) {
                            is FileCommitResult.Success -> {
                                _editEvents.trySend(FileEditEvent.Committed(path, conflict.branch, isNewBranch = false))
                                finishEditAndRefresh(conflict.branch)
                            }

                            is FileCommitResult.Conflict -> {
                                _uiState.update { it.copy(editState = conflict.copy(latestSha = result.latestSha)) }
                            }
                        }
                    },
                    onFailure = { e ->
                        _editEvents.trySend(FileEditEvent.Failed(mapError(e)))
                        _uiState.update { it.copy(editState = conflict) }
                    },
                )
            }
        }

        /**
         * 409「保留本地更改」：本地文本经 [FileEditEvent.KeepLocal] 上抛（UI 复制剪贴板），
         * 远端保持不变；随后关闭编辑器返回（查看器保留旧内容）。
         */
        fun keepLocalAfterConflict() {
            val conflict = _uiState.value.editState as? FileEditState.Conflict ?: return
            if (conflict.operation == ConflictOperation.UPDATE) {
                _editEvents.trySend(FileEditEvent.KeepLocal(conflict.localText.orEmpty()))
            }
            _uiState.update { it.copy(editState = FileEditState.Idle) }
        }

        /** 提交/删除成功后的收尾：清查看器与编辑态 + 失效树缓存并按目标分支重载（AC4 缓存失效）。 */
        private fun finishEditAndRefresh(targetRef: String?) {
            val ref = targetRef ?: loadedRef
            _uiState.update {
                it.copy(
                    selectedPath = null,
                    fileState = FileViewState.Idle,
                    editState = FileEditState.Idle,
                    treeState = TreeState.Loading,
                )
            }
            loadedRef = null
            if (ref != null) loadRootTree(ref)
        }

        /** 重新拉取查看器内容（删除冲突「重载」用：展示最新文件）。 */
        private fun refreshViewerContent(path: String) {
            viewModelScope.launch {
                repoRepository.getFileContent(owner, repo, path, loadedRef).fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(fileState = FileViewState.Loaded(data)) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(fileState = FileViewState.Error(mapError(e))) }
                    },
                )
            }
        }

        private inline fun updateTree(transform: (List<GitTreeNode>) -> List<GitTreeNode>): TreeState {
            val current = _uiState.value.treeState as? TreeState.Loaded ?: return TreeState.Loading
            return TreeState.Loaded(transform(current.rootNodes))
        }

        /** 异常 → 错误类型（404 → NOT_FOUND，IO → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): RepoErrorType =
            when {
                e is HttpException && (e.code() == 401 || e.code() == 403) -> RepoErrorType.FORBIDDEN
                e is HttpException && e.code() == 404 -> RepoErrorType.NOT_FOUND
                e is IOException -> RepoErrorType.NETWORK
                else -> RepoErrorType.UNKNOWN
            }
    }

/**
 * 文件编辑流程事件（T22；UI 层消费——Snackbar 文案 / 剪贴板复制）。
 */
sealed interface FileEditEvent {
    /** 提交成功（含覆盖路径）。 */
    data class Committed(
        val path: String,
        val branch: String?,
        val isNewBranch: Boolean,
    ) : FileEditEvent

    /** 删除成功。 */
    data class Deleted(
        val path: String,
    ) : FileEditEvent

    /** 「保留本地更改」：携带用户文本，UI 复制到剪贴板后提示。 */
    data class KeepLocal(
        val text: String,
    ) : FileEditEvent

    /** 写操作失败（错误类型驱动本地化文案）。 */
    data class Failed(
        val errorType: RepoErrorType,
    ) : FileEditEvent
}
