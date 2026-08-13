@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底，T13 细化异常类型

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Issue 详情页 ViewModel。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo]/[number] 导航参数，加载 Issue 详情与时间线。
 * 错误一律映射为 [IssueErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 */
@HiltViewModel
class IssueDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IssueRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])
        private val number: Int = checkNotNull(savedStateHandle["number"])

        private val _uiState = MutableStateFlow<IssueDetailUiState>(IssueDetailUiState.Loading)
        val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

        init {
            loadIssueDetail()
        }

        fun retry() {
            _uiState.value = IssueDetailUiState.Loading
            loadIssueDetail()
        }

        private fun loadIssueDetail() {
            viewModelScope.launch {
                _uiState.value = IssueDetailUiState.Loading
                try {
                    val issue = repository.getIssue(owner, repo, number)
                    val timeline: List<IssueTimelineItem> = repository.timeline(owner, repo, number)
                    _uiState.value = IssueDetailUiState.Success(issue = issue, timeline = timeline)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = IssueDetailUiState.Error(errorType = e.toIssueErrorType())
                }
            }
        }
    }
