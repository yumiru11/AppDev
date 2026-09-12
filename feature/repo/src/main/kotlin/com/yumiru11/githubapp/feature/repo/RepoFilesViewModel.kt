@file:Suppress("TooGenericExceptionCaught", "TooManyFunctions")
// - TooGenericExceptionCaught：网络/IO 错误统一兜底（同 RepoDetailViewModel 先例）
// - TooManyFunctions（25 ≥ 20）：本类是「仓库」分区**唯一状态层**（树/目录/文件/编辑提交/文件内
//   查找），查找的 6 个方法均为对 core:editor 状态机的薄转发（一行 update），拆类反而把
//   同一屏幕的状态源切成两处（IssueDetailScreen 同款装配豁免先例）

package com.yumiru11.githubapp.feature.repo

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftKey
import com.yumiru11.githubapp.core.datastore.draft.DraftTargets
import com.yumiru11.githubapp.core.editor.FileFindState
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
 * - **编辑草稿持久化**（需求审计 2026-09-11 §10 P2；plan.md:955「文件编辑冲突 → 本地草稿保留」）：
 *   编辑中的文本经 [DraftAutoSaver] 防抖落盘（键 = owner/repo/ref/path），重新进入同一文件时恢复
 *   并上抛 [FileEditEvent.DraftRestored]（UI 提示 + 「丢弃草稿」）；提交/删除成功、409「重载」、
 *   文本回到基线、用户主动丢弃都会清草稿；[dismissEdit] 退出时**立即落盘**（进程被杀不再丢工作）。
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
        private val drafts: DraftAutoSaver,
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

        /**
         * 当前编辑会话的草稿上下文（进入编辑态时确定，离开编辑/提交成功后清空）。
         *
         * @param key 草稿键（文件 = owner/repo/ref/path；新建文件 = owner/repo/ref）
         * @param baseText 进入编辑时的远端基线文本 —— 「丢弃草稿」把编辑区恢复成它，
         *   也是"无未提交内容"的判据（编辑区回到基线 → 清草稿而不是存一份和远端一样的草稿）
         */
        private data class EditDraft(
            val key: DraftKey,
            val baseText: String,
        )

        private var editDraft: EditDraft? = null

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
            _uiState.update {
                it.copy(
                    selectedPath = node.path,
                    fileState = FileViewState.Loading,
                    isFindOpen = false,
                    findState = FileFindState(),
                )
            }
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
            _uiState.update {
                it.copy(fileState = FileViewState.Loading, isFindOpen = false, findState = FileFindState())
            }
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
            _uiState.update {
                it.copy(
                    selectedPath = path,
                    fileState = FileViewState.Loading,
                    isFindOpen = false,
                    findState = FileFindState(),
                )
            }
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
            _uiState.update {
                it.copy(
                    selectedPath = null,
                    fileState = FileViewState.Idle,
                    isFindOpen = false,
                    findState = FileFindState(),
                )
            }
        }

        // ─── #166 / UI14 文件内查找（代码浏览屏）──────────────────────────────
        //
        // 职责边界：本层是查找状态的**展示事实源**（序号/计数/面板开合），纯逻辑在
        // core:editor 的 [FileFindState]（单测 FileFindStateTest）；真正的高亮与跳转由
        // View 侧的 [com.yumiru11.githubapp.core.editor.CodeEditorController] 执行
        // （编辑器句柄只在组合期存在，不能塞进 ViewModel）。
        //
        // [onFindNext]/[onFindPrevious] 乐观推进序号（立即刷新「第 n / 共 m 项」），
        // [onFindResults] 随后回灌编辑器匹配表的权威值收敛（与 Sora 环形跳转语义一致）。

        /** 展开查找面板（代码文件；查询词为空，等待输入）。 */
        fun openFind() {
            _uiState.update { it.copy(isFindOpen = true) }
        }

        /** 关闭查找面板并复位状态（编辑器高亮由 View 侧 clearFindText 清除）。 */
        fun closeFind() {
            _uiState.update { it.copy(isFindOpen = false, findState = FileFindState()) }
        }

        /** 查询词变更：计数与序号立即复位（匹配结果由编辑器异步回灌 [onFindResults]）。 */
        fun onFindQueryChanged(query: String) {
            _uiState.update { it.copy(findState = it.findState.withQuery(query)) }
        }

        /** 下一处匹配：乐观推进序号（循环语义，回绕由 [FileFindState.cycledNext] 负责）。 */
        fun onFindNext() {
            _uiState.update { it.copy(findState = it.findState.cycledNext()) }
        }

        /** 上一处匹配：乐观回退序号（首项之前回到末项）。 */
        fun onFindPrevious() {
            _uiState.update { it.copy(findState = it.findState.cycledPrevious()) }
        }

        /** 回灌编辑器的权威查找结果（匹配总数 / 当前序号；越界序号在状态机内收敛）。 */
        fun onFindResults(result: FileFindState) {
            _uiState.update {
                it.copy(findState = it.findState.withResults(result.matchCount, result.currentMatchIndex))
            }
        }

        // ─── T22 文件编辑提交 ─────────────────────────────────────────────────

        /** 当前查看的文件进入编辑模式（查看器「编辑」按钮；仅文本文件 CODE/MARKDOWN）。 */
        fun startEdit() {
            val data = (_uiState.value.fileState as? FileViewState.Loaded)?.data ?: return
            if (data.kind != FileKind.CODE && data.kind != FileKind.MARKDOWN) return
            val base = data.text.orEmpty()
            val draftKey = draftTargetKey(path = _uiState.value.selectedPath)
            editDraft = EditDraft(draftKey, baseText = base)
            // 同步先进入编辑态（远端内容立即可编辑），草稿恢复在后台读盘完成后回填 ——
            // 不让一次磁盘读阻塞「点编辑」的响应
            _uiState.update {
                it.copy(
                    editState =
                        FileEditState.Editing(
                            isNew = false,
                            text = base,
                            sha = data.sha,
                            isMarkdown = data.kind == FileKind.MARKDOWN,
                        ),
                )
            }
            restoreDraft(draftKey = draftKey, baseText = base)
        }

        /** 新建文件模式（文件 Tab「新建文件」按钮；路径由提交对话框输入）。 */
        fun startNewFile() {
            val draftKey = draftTargetKey(path = null)
            editDraft = EditDraft(draftKey, baseText = "")
            _uiState.update {
                it.copy(
                    selectedPath = null,
                    fileState = FileViewState.Idle,
                    editState = FileEditState.Editing(isNew = true, text = "", sha = null, isMarkdown = false),
                )
            }
            restoreDraft(draftKey = draftKey, baseText = "")
        }

        /** 编辑器文本变更同步（编辑器是文本唯一事实源；提交/预览用 [FileEditState.Editing.text]）。 */
        fun onEditorTextChanged(text: String) {
            val current = _uiState.value.editState as? FileEditState.Editing ?: return
            _uiState.update { it.copy(editState = current.copy(text = text)) }
            val draft = editDraft ?: return
            if (text == draft.baseText) {
                // 回到基线（撤销到底/清空）＝ 没有未提交内容：清草稿，既避免"空草稿"，
                // 也避免下次进来弹一个内容与远端一模一样的恢复提示
                drafts.discard(draft.key)
                return
            }
            drafts.scheduleSave(draft.key, text)
        }

        /**
         * 关闭编辑（返回查看器/树）。
         *
         * 编辑内容**立即落盘为草稿**（需求审计 §10 P2：进程被杀不再丢工作）——
         * 再次进入同一文件会恢复并提示，用户要"真丢弃"走 [discardRestoredDraft] 或 409 三选项。
         */
        fun dismissEdit() {
            editDraft?.let { draft ->
                (_uiState.value.editState as? FileEditState.Editing)?.let { drafts.saveNow(draft.key, it.text) }
            }
            editDraft = null
            _uiState.update { it.copy(editState = FileEditState.Idle) }
        }

        /**
         * 「丢弃草稿」（恢复提示的 Snackbar 动作）：删除已存草稿并把编辑区恢复为进入编辑时的远端基线文本。
         *
         * 恢复到基线而不是"保留当前文本"：否则紧接着的防抖保存会把刚丢弃的内容又写回去
         * （且文本等于基线时 [onEditorTextChanged] 的判定也会再清一次，语义自洽）。
         */
        fun discardRestoredDraft() {
            val draft = editDraft ?: return
            drafts.discard(draft.key)
            val current = _uiState.value.editState as? FileEditState.Editing ?: return
            _uiState.update { it.copy(editState = current.copy(text = draft.baseText)) }
        }

        /** 当前编辑目标的草稿键（路径未知 = 新建文件模式 → 按仓库 + 分支一个草稿槽）。 */
        private fun draftTargetKey(path: String?): DraftKey {
            val ref = loadedRef ?: refArg
            return if (path.isNullOrBlank()) {
                DraftTargets.newFile(owner, repo, ref)
            } else {
                DraftTargets.fileEdit(owner, repo, ref, path)
            }
        }

        /**
         * 草稿恢复（读盘在后台，完成后回填编辑区）。
         *
         * 三重守卫，缺一不可：
         * 1. 读不到草稿 / 读失败 → 什么都不做（[DraftAutoSaver.load] 已把 IO 失败降级为 null）；
         * 2. 草稿与远端基线**相同** → 不做"假恢复"（也不弹提示）；
         * 3. 用户已经开始输入（编辑区文本 != 基线）→ **绝不覆盖**用户正在写的内容（读盘是异步的）。
         */
        private fun restoreDraft(
            draftKey: DraftKey,
            baseText: String,
        ) {
            viewModelScope.launch {
                val draft = drafts.load(draftKey) ?: return@launch
                if (draft == baseText) return@launch
                val current = _uiState.value.editState as? FileEditState.Editing ?: return@launch
                if (current.text != baseText) return@launch
                _uiState.update { it.copy(editState = current.copy(text = draft)) }
                _editEvents.trySend(FileEditEvent.DraftRestored)
            }
        }

        /** 清草稿并结束草稿会话（提交/删除成功、409「重载」、离开编辑态）。 */
        private fun discardEditDraft() {
            editDraft?.let { drafts.discard(it.key) }
            editDraft = null
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
                // 远端已删：本地文本无处可提交，草稿一并作废（否则下次进入恢复出一份永远提交不了的文本）
                discardEditDraft()
                _uiState.update { it.copy(editState = FileEditState.Idle) }
                refreshViewerContent(path)
                return
            }
            viewModelScope.launch {
                repoRepository.getFileContent(owner, repo, path, conflict.branch ?: loadedRef).fold(
                    onSuccess = { data ->
                        val reloaded = data.text.orEmpty()
                        // 409「重载」＝用户显式选择远端版本：旧草稿作废（不删的话下次进入会把被放弃的
                        // 本地文本又恢复出来），并把重载文本设为新基线 —— 会话继续，后续输入仍有草稿保护
                        editDraft?.let { drafts.discard(it.key) }
                        editDraft = editDraft?.copy(baseText = reloaded)
                        _uiState.update {
                            it.copy(
                                fileState = FileViewState.Loaded(data),
                                editState =
                                    FileEditState.Editing(
                                        isNew = false,
                                        text = reloaded,
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

        /** 提交/删除成功后的收尾：清草稿 + 清查看器与编辑态 + 失效树缓存并按目标分支重载（AC4 缓存失效）。 */
        private fun finishEditAndRefresh(targetRef: String?) {
            // 内容已落到远端：草稿使命结束（保留会让下次进入恢复出与远端相同的文本）
            discardEditDraft()
            val ref = targetRef ?: loadedRef
            _uiState.update {
                it.copy(
                    selectedPath = null,
                    fileState = FileViewState.Idle,
                    editState = FileEditState.Idle,
                    treeState = TreeState.Loading,
                    isFindOpen = false,
                    findState = FileFindState(),
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

        /**
         * 异常 → 错误域。
         *
         * **404 一律是「路径不存在」而非「仓库不存在」**（#201 P0）：本 ViewModel 的每个
         * 请求都发生在仓库已加载之后（`GET /repos/{o}/{r}` 已 200 才可能走到这里），
         * 所以 contents/blob 的 404 只能说明**该文件/目录已删除或改名**。旧实现映射为
         * [RepoErrorType.NOT_FOUND]，界面把「文件不存在」说成「Repository not found」，
         * 还配了一个必然再次 404 的 Retry（CI 帧 `editor.png` 实证）。
         */
        private fun mapError(e: Throwable): RepoErrorType {
            val type =
                when {
                    e is HttpException && (e.code() == 401 || e.code() == 403) -> RepoErrorType.FORBIDDEN
                    e is HttpException && e.code() == 404 -> RepoErrorType.PATH_NOT_FOUND
                    e is IOException -> RepoErrorType.NETWORK
                    else -> RepoErrorType.UNKNOWN
                }
            // 失败留档：错误域映射是排查的起点（CI logcat 过滤 RepoFiles 即可定位是
            // 「路径没了」还是「网络挂了」；#201 的问题正是靠 logcat 里的 contents 404 反查出来的）
            Log.i(TAG, "loadFailed type=$type httpCode=${(e as? HttpException)?.code()} cause=${e.javaClass.simpleName}")
            return type
        }
    }

/**
 * 文件域加载失败日志 tag（CI/真机 logcat 过滤：`adb logcat -s RepoFiles`）。
 *
 * #201 的定位链就是靠 logcat 里的 `contents/README.md → 404` 反查出来的；
 * 之前这条路径**一行日志都没有**，只能靠 UI 截图猜。
 */
private const val TAG = "RepoFiles"

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

    /**
     * 进入编辑态时恢复了一份本地草稿（编辑区已被回填）。
     *
     * UI 消费 = Snackbar 提示 + 「丢弃草稿」动作（[RepoFilesViewModel.discardRestoredDraft]）；
     * 用户无动作时草稿保留（继续编辑会自动续存）。
     */
    data object DraftRestored : FileEditEvent

    /** 写操作失败（错误类型驱动本地化文案）。 */
    data class Failed(
        val errorType: RepoErrorType,
    ) : FileEditEvent
}
