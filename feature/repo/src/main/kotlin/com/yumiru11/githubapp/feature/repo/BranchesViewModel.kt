@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底（RepoDetailViewModel 先例）

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
 * 分支管理页 ViewModel（T23，plan.md §7.5）。
 *
 * - 加载：仓库写控制（[BranchControl] 权限 + 默认分支）+ 分支列表（默认分支排首）
 * - 新建分支：基于默认分支经 Git Refs API 创建；成功后刷新列表并上抛事件
 * - 删除分支：仅非默认分支可见入口；422（默认分支/受保护）映射为失败事件
 * - 切换分支由 UI 层回调宿主处理（文件树重载），不进本 VM 状态机
 */
@HiltViewModel
class BranchesViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repoRepository: RepoRepository,
        private val repoManagementRepository: RepoManagementRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _uiState = MutableStateFlow<BranchesUiState>(BranchesUiState.Loading)
        val uiState: StateFlow<BranchesUiState> = _uiState.asStateFlow()

        private val _events = Channel<BranchEvent>(Channel.BUFFERED)
        val events: Flow<BranchEvent> = _events.receiveAsFlow()

        init {
            load()
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is BranchesUiState.Error) load()
        }

        /** 基于默认分支新建分支（空白名/进行中忽略）。 */
        fun createBranch(name: String) {
            val state = _uiState.value as? BranchesUiState.Success ?: return
            val trimmed = name.trim()
            if (trimmed.isBlank() || state.isBusy) return
            _uiState.update { s -> if (s is BranchesUiState.Success) s.copy(isBusy = true) else s }
            viewModelScope.launch {
                repoRepository.createBranch(owner, repo, trimmed, state.defaultBranch ?: DEFAULT_BRANCH).fold(
                    onSuccess = {
                        _events.trySend(BranchEvent.Created(trimmed))
                        reload()
                    },
                    onFailure = { e ->
                        _uiState.update { s -> if (s is BranchesUiState.Success) s.copy(isBusy = false) else s }
                        _events.trySend(BranchEvent.Failed(mapError(e)))
                    },
                )
            }
        }

        /** 删除分支（调用方已确认；默认分支入口不可见，GitHub 422 兜底为失败事件）。 */
        fun deleteBranch(name: String) {
            val state = _uiState.value as? BranchesUiState.Success ?: return
            if (state.isBusy) return
            _uiState.update { s -> if (s is BranchesUiState.Success) s.copy(isBusy = true) else s }
            viewModelScope.launch {
                runCatching { repoManagementRepository.deleteBranch(owner, repo, name) }.fold(
                    onSuccess = {
                        _events.trySend(BranchEvent.Deleted(name))
                        reload()
                    },
                    onFailure = { e ->
                        _uiState.update { s -> if (s is BranchesUiState.Success) s.copy(isBusy = false) else s }
                        _events.trySend(BranchEvent.Failed(mapError(e)))
                    },
                )
            }
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = BranchesUiState.Loading
                val control = repoRepository.branchControl(owner, repo)
                repoRepository.branches(owner, repo).fold(
                    onSuccess = { list ->
                        _uiState.value =
                            BranchesUiState.Success(
                                branches = list.sortedWith(compareBy({ it.name != control.defaultBranch }, { it.name })),
                                defaultBranch = control.defaultBranch,
                                canPush = control.canPush,
                            )
                    },
                    onFailure = { e ->
                        _uiState.value = BranchesUiState.Error(errorType = mapError(e))
                    },
                )
            }
        }

        /** 新建/删除成功后的静默刷新（不闪 Loading，保留操作完成态）。 */
        private fun reload() {
            viewModelScope.launch {
                val control = repoRepository.branchControl(owner, repo)
                repoRepository.branches(owner, repo).fold(
                    onSuccess = { list ->
                        _uiState.value =
                            BranchesUiState.Success(
                                branches = list.sortedWith(compareBy({ it.name != control.defaultBranch }, { it.name })),
                                defaultBranch = control.defaultBranch,
                                canPush = control.canPush,
                            )
                    },
                    onFailure = { e ->
                        _uiState.update { s -> if (s is BranchesUiState.Success) s.copy(isBusy = false) else s }
                        _events.trySend(BranchEvent.Failed(mapError(e)))
                    },
                )
            }
        }

        /** 异常 → 错误类型（404 → NOT_FOUND，IO → NETWORK，401/403 → FORBIDDEN，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): RepoErrorType =
            when {
                e is HttpException && (e.code() == 401 || e.code() == 403) -> RepoErrorType.FORBIDDEN
                e is HttpException && e.code() == 404 -> RepoErrorType.NOT_FOUND
                e is IOException -> RepoErrorType.NETWORK
                else -> RepoErrorType.UNKNOWN
            }

        private companion object {
            const val DEFAULT_BRANCH = "main"
        }
    }
