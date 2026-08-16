@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底（同 RepoDetailViewModel 先例）

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 仓库文件浏览 ViewModel（T11 文件树 + 代码浏览）。
 *
 * - 根树：首次进入文件 Tab 加载（ref = 默认分支，UI 传入；同 ref 不重复加载）
 * - 目录：点击展开 → 首次按需取子树（[GitTreeNode.children] == null），
 *   失败保持收起（保留重试机会）；再次点击收起
 * - 文件：点击 → 加载内容（分类判定在 RepoRepository，本层只透传状态）
 *
 * 错误一律映射为 [RepoErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
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

        private val _uiState = MutableStateFlow(RepoFilesUiState())
        val uiState: StateFlow<RepoFilesUiState> = _uiState.asStateFlow()

        /** 已加载的根树分支（同 ref 免重复拉取；Tab 切换重建组合不触发网络） */
        private var loadedRef: String? = null

        fun loadRootTree(ref: String) {
            if (loadedRef == ref && _uiState.value.treeState is TreeState.Loaded) return
            loadedRef = ref
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

        fun closeFile() {
            _uiState.update { it.copy(selectedPath = null, fileState = FileViewState.Idle) }
        }

        private inline fun updateTree(transform: (List<GitTreeNode>) -> List<GitTreeNode>): TreeState {
            val current = _uiState.value.treeState as? TreeState.Loaded ?: return TreeState.Loading
            return TreeState.Loaded(transform(current.rootNodes))
        }

        /** 异常 → 错误类型（404 → NOT_FOUND，IO → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): RepoErrorType =
            when {
                e is HttpException && e.code() == 404 -> RepoErrorType.NOT_FOUND
                e is IOException -> RepoErrorType.NETWORK
                else -> RepoErrorType.UNKNOWN
            }
    }
