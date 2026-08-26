@file:Suppress("TooGenericExceptionCaught")
// 数据流构造期异常统一兜底为 Error 态（同 IssueListViewModel 先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PR 列表页 ViewModel（T15）。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo] 导航参数，按 [filter] 构造分页流。
 * GitHub PR 公开可读，无需登录门禁（GuestTokenProvider 覆盖 API 认证）。
 * 切换 Open/Closed/All 过滤时重建分页流；下拉刷新由 UI 层 LazyPagingItems.refresh() 触发。
 */
@HiltViewModel
class PullRequestListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PullRequestRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _filter = MutableStateFlow(PullRequestFilter.OPEN)
        val filter: StateFlow<PullRequestFilter> = _filter.asStateFlow()

        private val _uiState = MutableStateFlow<PullRequestListUiState>(PullRequestListUiState.Loading)
        val uiState: StateFlow<PullRequestListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        /** 切换 Open/Closed/All 过滤：更新过滤态并重建分页流 */
        fun setFilter(filter: PullRequestFilter) {
            if (_filter.value == filter) return
            _filter.value = filter
            load()
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is PullRequestListUiState.Error) {
                load()
            }
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = PullRequestListUiState.Loading
                try {
                    val pulls = repository.pulls(owner, repo, _filter.value).cachedIn(viewModelScope)
                    // T23：仓库推送权限（创建 PR 入口显隐；repositoryControl 失败保守隐藏，不阻塞列表）
                    val permission = repository.repositoryControl(owner, repo).viewerPermission
                    _uiState.value =
                        PullRequestListUiState.Success(
                            pulls = pulls,
                            canCreatePullRequest = permission == ViewerPermission.WRITE,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = PullRequestListUiState.Error(errorType = e.toPullRequestErrorType())
                }
            }
        }
    }
