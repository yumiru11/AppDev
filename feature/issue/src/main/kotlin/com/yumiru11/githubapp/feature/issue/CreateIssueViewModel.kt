@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：网络/IO 错误统一兜底为 Error 态（同 IssueDetailViewModel 先例）
// - SwallowedException：标签候选项加载失败降级为空列表（#163 L02），创建 Issue 仍可无标签提交

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftTargets
import com.yumiru11.githubapp.core.datastore.draft.DraftText
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 创建 Issue ViewModel（T14）。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo]，提交标题/正文/标签（逗号分隔）到
 * [IssueRepository.createIssue]。成功后 emit [CreateIssueEvent.Created]（UI 层返回列表）。
 */
@HiltViewModel
class CreateIssueViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IssueRepository,
        private val drafts: DraftAutoSaver,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        /**
         * 正文草稿（长文本；标题短单行不进草稿，与 `DraftTargets.newIssue` 的取舍一致）。
         * 进程被杀后重进表单时自动恢复。
         */
        val bodyDraft: DraftText = DraftText(DraftTargets.newIssue(owner, repo), baseline = "", saver = drafts, scope = viewModelScope)

        private val _uiState = MutableStateFlow<CreateIssueUiState>(CreateIssueUiState.Idle)
        val uiState: StateFlow<CreateIssueUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<CreateIssueEvent>(extraBufferCapacity = EVENT_BUFFER)
        val events: SharedFlow<CreateIssueEvent> = _events.asSharedFlow()

        /** #163 L02：仓库标签候选项（加载失败/无权限 → 空列表，仍可无标签提交） */
        private val _availableLabels = MutableStateFlow<List<IssueLabel>>(emptyList())
        val availableLabels: StateFlow<List<IssueLabel>> = _availableLabels.asStateFlow()

        init {
            loadLabels()
        }

        /** 创建 Issue；[labels] 为选中的标签名（空列表 → null，不携带 labels 字段） */
        fun createIssue(
            title: String,
            body: String,
            labels: List<String>,
        ) {
            if (title.isBlank()) return
            viewModelScope.launch {
                _uiState.value = CreateIssueUiState.Submitting
                try {
                    repository.createIssue(owner, repo, title.trim(), body, labels.ifEmpty { null })
                    bodyDraft.discard()
                    _events.emit(CreateIssueEvent.Created)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = CreateIssueUiState.Error(errorType = e.toIssueErrorType())
                }
            }
        }

        /** 加载标签候选项（失败保守降级为空列表；创建表单不因候选项失败而不可用） */
        private fun loadLabels() {
            viewModelScope.launch {
                _availableLabels.value =
                    try {
                        repository.getLabels(owner, repo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
            }
        }

        private companion object {
            const val EVENT_BUFFER = 8
        }
    }

/** 创建 Issue 页 UI 状态 */
sealed interface CreateIssueUiState {
    data object Idle : CreateIssueUiState

    data object Submitting : CreateIssueUiState

    data class Error(
        val errorType: IssueErrorType,
    ) : CreateIssueUiState
}

/** 创建 Issue 页事件通道 */
sealed interface CreateIssueEvent {
    /** 创建成功（UI 层返回列表页） */
    data object Created : CreateIssueEvent
}
