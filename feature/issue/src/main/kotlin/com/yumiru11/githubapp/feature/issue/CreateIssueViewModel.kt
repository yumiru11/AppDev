@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底为 Error 态（同 IssueDetailViewModel 先例）

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
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
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _uiState = MutableStateFlow<CreateIssueUiState>(CreateIssueUiState.Idle)
        val uiState: StateFlow<CreateIssueUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<CreateIssueEvent>(extraBufferCapacity = EVENT_BUFFER)
        val events: SharedFlow<CreateIssueEvent> = _events.asSharedFlow()

        /** 创建 Issue；[labels] 为逗号分隔标签名（空串 → null） */
        fun createIssue(
            title: String,
            body: String,
            labels: String,
        ) {
            if (title.isBlank()) return
            viewModelScope.launch {
                _uiState.value = CreateIssueUiState.Submitting
                try {
                    val labelList = labels.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    repository.createIssue(owner, repo, title.trim(), body, labelList.ifEmpty { null })
                    _events.emit(CreateIssueEvent.Created)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = CreateIssueUiState.Error(errorType = e.toIssueErrorType())
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
