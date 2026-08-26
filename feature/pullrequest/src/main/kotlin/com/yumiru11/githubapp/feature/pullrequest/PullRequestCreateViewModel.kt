@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底（PullRequestListViewModel 先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
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
import javax.inject.Inject

/**
 * 创建 PR 页 ViewModel（T23，plan.md §7.3/§7.5）。
 *
 * - 加载：仓库写控制（推送权限 + 默认分支）+ 分支列表（默认分支排首）
 * - 初始：base = 默认分支，head = 第一个非 base 分支（无 → head 留空，提交禁用）
 * - 提交：标题必填、head != base、有权限、非重入；成功 → [PullRequestCreateEvent.Created]
 * - 失败：回退提交态并上抛错误事件（UI 层映射 Snackbar 文案）
 */
@HiltViewModel
class PullRequestCreateViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PullRequestRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _uiState = MutableStateFlow<PullRequestCreateUiState>(PullRequestCreateUiState.Loading)
        val uiState: StateFlow<PullRequestCreateUiState> = _uiState.asStateFlow()

        private val _events = Channel<PullRequestCreateEvent>(Channel.BUFFERED)
        val events: Flow<PullRequestCreateEvent> = _events.receiveAsFlow()

        init {
            load()
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is PullRequestCreateUiState.Error) load()
        }

        fun updateTitle(title: String) {
            _uiState.update { state -> if (state is PullRequestCreateUiState.Form) state.copy(title = title) else state }
        }

        fun updateBody(body: String) {
            _uiState.update { state -> if (state is PullRequestCreateUiState.Form) state.copy(body = body) else state }
        }

        fun selectBase(branch: String) {
            _uiState.update { state -> if (state is PullRequestCreateUiState.Form) state.copy(baseBranch = branch) else state }
        }

        fun selectHead(branch: String) {
            _uiState.update { state -> if (state is PullRequestCreateUiState.Form) state.copy(headBranch = branch) else state }
        }

        /** 提交创建 PR（校验不通过/重入/无权限直接忽略）。 */
        fun submit() {
            val state = _uiState.value as? PullRequestCreateUiState.Form ?: return
            val title = state.title.trim()
            if (title.isBlank() || state.isSubmitting || !state.canCreate) return
            if (state.headBranch.isBlank() || state.headBranch == state.baseBranch) return
            _uiState.update { s -> if (s is PullRequestCreateUiState.Form) s.copy(isSubmitting = true) else s }
            viewModelScope.launch {
                runCatching {
                    repository.createPullRequest(
                        owner = owner,
                        repo = repo,
                        title = title,
                        body = state.body.trim().takeIf { it.isNotEmpty() },
                        head = state.headBranch,
                        base = state.baseBranch,
                    )
                }.fold(
                    onSuccess = { pullRequest ->
                        // 复位提交态（宿主导航离开前按钮仍可用，防重复点击）
                        _uiState.update { s -> if (s is PullRequestCreateUiState.Form) s.copy(isSubmitting = false) else s }
                        _events.trySend(PullRequestCreateEvent.Created(pullRequest.number))
                    },
                    onFailure = { e ->
                        if (e is CancellationException) {
                            throw e
                        }
                        _uiState.update { s -> if (s is PullRequestCreateUiState.Form) s.copy(isSubmitting = false) else s }
                        _events.trySend(PullRequestCreateEvent.Failed(e.toPullRequestErrorType()))
                    },
                )
            }
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = PullRequestCreateUiState.Loading
                try {
                    val control = repository.repositoryControl(owner, repo)
                    repository.branches(owner, repo).fold(
                        onSuccess = { names ->
                            val default = control.defaultBranch
                            val ordered = names.sortedWith(compareBy({ it != default }, { it }))
                            val branches = ordered.mapIndexed { index, name -> RepositoryBranch(name = name, isDefault = name == default) }
                            val base = default ?: branches.firstOrNull()?.name.orEmpty()
                            val head = branches.firstOrNull { it.name != base }?.name.orEmpty()
                            _uiState.value =
                                PullRequestCreateUiState.Form(
                                    branches = branches,
                                    canCreate = control.viewerPermission == ViewerPermission.WRITE,
                                    baseBranch = base,
                                    headBranch = head,
                                )
                        },
                        onFailure = { e ->
                            _uiState.value = PullRequestCreateUiState.Error(errorType = e.toPullRequestErrorType())
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = PullRequestCreateUiState.Error(errorType = e.toPullRequestErrorType())
                }
            }
        }
    }
