@file:Suppress("TooGenericExceptionCaught")
// 创建失败统一在仓库层归一化为 GitHubError，这里只做事件映射；异常即事件，无需再抛。

package com.yumiru11.githubapp.feature.repo

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
 * 新建仓库页 ViewModel（L04）。
 *
 * - 表单编辑：名（实时校验）/描述/私有开关/README 初始化开关
 * - 提交：校验通过 → [RepoAdminRepository.createRepository] → 成功写入 [CreateRepoUiState.created]
 *   （UI 据此导航仓库详情），失败按 [GitHubError] 映射 [CreateRepoEvent]
 * - 防重入：提交中忽略重复提交（[CreateRepoUiState.isSubmitting]）
 */
@HiltViewModel
class CreateRepoViewModel
    @Inject
    constructor(
        private val repoAdminRepository: RepoAdminRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CreateRepoUiState())
        val uiState: StateFlow<CreateRepoUiState> = _uiState.asStateFlow()

        private val _events = Channel<CreateRepoEvent>(Channel.BUFFERED)
        val events: Flow<CreateRepoEvent> = _events.receiveAsFlow()

        /** 名称输入：空白不报错（避免刚进页面就飘红），非空按 trim 后立即校验（容忍粘贴尾随空格） */
        fun onNameChange(value: String) {
            val error = if (value.isBlank()) null else RepoNameValidator.error(value.trim())
            _uiState.update { it.copy(name = value, nameError = error) }
        }

        fun onDescriptionChange(value: String) {
            _uiState.update { it.copy(description = value) }
        }

        fun onPrivateChange(value: Boolean) {
            _uiState.update { it.copy(isPrivate = value) }
        }

        fun onAutoInitChange(value: Boolean) {
            _uiState.update { it.copy(autoInit = value) }
        }

        /** 提交创建；名校验失败只更新 [CreateRepoUiState.nameError]，不发请求。 */
        fun submit() {
            val state = _uiState.value
            if (state.isSubmitting) return
            val name = state.name.trim()
            val error = RepoNameValidator.error(name)
            if (error != null) {
                _uiState.update { it.copy(nameError = error) }
                return
            }
            _uiState.update { it.copy(isSubmitting = true, nameError = null) }
            viewModelScope.launch {
                repoAdminRepository
                    .createRepository(
                        name = name,
                        description = state.description.trim().ifBlank { null },
                        isPrivate = state.isPrivate,
                        autoInit = state.autoInit,
                    ).fold(
                        onSuccess = { repo ->
                            _uiState.update {
                                it.copy(isSubmitting = false, created = RepoRef(repo.ownerLogin, repo.name))
                            }
                        },
                        onFailure = { e ->
                            _uiState.update { it.copy(isSubmitting = false) }
                            _events.send(mapFailure(e))
                        },
                    )
            }
        }

        /** 失败原因 → 事件（422 重名/非法、403 无权限、其余通用失败）。 */
        private fun mapFailure(e: Throwable): CreateRepoEvent =
            when ((e as? GitHubRequestException)?.error) {
                GitHubError.Validation -> CreateRepoEvent.NameTaken
                GitHubError.Forbidden -> CreateRepoEvent.PermissionDenied
                else -> CreateRepoEvent.Failed
            }
    }
