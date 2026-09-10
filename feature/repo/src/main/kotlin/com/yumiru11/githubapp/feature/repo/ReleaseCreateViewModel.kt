@file:Suppress("TooGenericExceptionCaught")
// 失败统一在仓库层归一到 GitHubError，这里只做事件映射；异常即事件，无需再抛。

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * 新建 Release 表单 ViewModel（L05）。
 *
 * - 从 SavedStateHandle 读 [owner]/[repo] 与可选的默认分支 [defaultRef]（宿主传入）
 * - 提交 → [RepoManagementRepository.createRelease]；成功后 [ReleaseCreateUiState.created] 置为 Tag
 * - 失败按 [GitHubError] 映射 [ReleaseCreateEvent]（422 校验 / 403 无权限 / 其余通用）
 */
@HiltViewModel
class ReleaseCreateViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repoManagementRepository: RepoManagementRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _uiState =
            MutableStateFlow(
                ReleaseCreateUiState(
                    // 目标分支预填默认分支（GitHub 网页同款默认值）
                    targetCommitish = savedStateHandle.get<String>("ref").orEmpty(),
                ),
            )
        val uiState: StateFlow<ReleaseCreateUiState> = _uiState.asStateFlow()

        private val _events = Channel<ReleaseCreateEvent>(Channel.BUFFERED)
        val events: Flow<ReleaseCreateEvent> = _events.receiveAsFlow()

        /** Tag 输入：非空即清除必填错误标记 */
        fun onTagChange(value: String) {
            _uiState.update { it.copy(tagName = value, tagError = if (value.isBlank()) it.tagError else false) }
        }

        fun onTargetChange(value: String) {
            _uiState.update { it.copy(targetCommitish = value) }
        }

        fun onNameChange(value: String) {
            _uiState.update { it.copy(name = value) }
        }

        fun onBodyChange(value: String) {
            _uiState.update { it.copy(body = value) }
        }

        fun onDraftChange(value: Boolean) {
            _uiState.update { it.copy(draft = value) }
        }

        fun onPrereleaseChange(value: Boolean) {
            _uiState.update { it.copy(prerelease = value) }
        }

        /** 提交发布；Tag 为空只标红不发请求。 */
        fun submit() {
            val state = _uiState.value
            if (state.isSubmitting) return
            if (state.tagName.isBlank()) {
                _uiState.update { it.copy(tagError = true) }
                return
            }
            _uiState.update { it.copy(isSubmitting = true, tagError = false) }
            viewModelScope.launch {
                repoManagementRepository
                    .createRelease(
                        owner = owner,
                        repo = repo,
                        release =
                            NewRelease(
                                tagName = state.tagName.trim(),
                                targetCommitish = state.targetCommitish.trim().ifBlank { null },
                                name = state.name.trim().ifBlank { null },
                                body = state.body.trim().ifBlank { null },
                                draft = state.draft,
                                prerelease = state.prerelease,
                            ),
                    ).fold(
                        onSuccess = { release ->
                            _uiState.update { it.copy(isSubmitting = false, created = release.tagName) }
                        },
                        onFailure = { e ->
                            _uiState.update { it.copy(isSubmitting = false) }
                            _events.send(mapFailure(e))
                        },
                    )
            }
        }

        /** 失败原因 → 事件（422 校验、403 无权限、其余通用失败）。 */
        private fun mapFailure(e: Throwable): ReleaseCreateEvent =
            when ((e as? GitHubRequestException)?.error) {
                GitHubError.Validation -> ReleaseCreateEvent.ValidationFailed
                GitHubError.Forbidden -> ReleaseCreateEvent.PermissionDenied
                else -> ReleaseCreateEvent.Failed
            }
    }
