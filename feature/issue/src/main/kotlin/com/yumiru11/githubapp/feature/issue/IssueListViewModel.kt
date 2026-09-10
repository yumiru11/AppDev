@file:Suppress("TooGenericExceptionCaught")
// 数据流构造期异常统一兜底为 Error 态（同 HomeViewModel/RepoDetailViewModel 先例）

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Issue 列表页 ViewModel（T13）。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo] 导航参数，按 [filter] 构造分页流。
 * GitHub Issue 公开可读，无需登录门禁（GuestTokenProvider 覆盖 API 认证）。
 * 切换 Open/Closed 过滤时重建分页流。
 *
 * 刷新语义（issue #165 / L07，两条路径最终都走 RemoteMediator REFRESH → 网络 + Room 合并）：
 * - 列表已有内容：UI 层 LazyPagingItems.refresh()（Paging 原生 invalidate，保留滚动位置与已渲染行）
 * - 列表空/错误：[refresh] 重建分页流（丢弃 cachedIn 缓存，从头跑一次 REFRESH）
 */
@HiltViewModel
class IssueListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IssueRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _filter = MutableStateFlow(IssueFilter.OPEN)
        val filter: StateFlow<IssueFilter> = _filter.asStateFlow()

        private val _uiState = MutableStateFlow<IssueListUiState>(IssueListUiState.Loading)
        val uiState: StateFlow<IssueListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        /** 切换 Open/Closed 过滤：更新过滤态并重建分页流 */
        fun setFilter(filter: IssueFilter) {
            if (_filter.value == filter) return
            _filter.value = filter
            load()
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is IssueListUiState.Error) {
                load()
            }
        }

        /**
         * 失效并重建分页流（invalidate 语义）：丢弃 cachedIn 缓存并重新构造 Pager，
         * 新的 RemoteMediator 会重新拉首屏写回 Room。空态下拉刷新走此路径
         * （已有内容时 UI 用 LazyPagingItems.refresh()，避免整列表闪回 Loading）。
         */
        fun refresh() {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = IssueListUiState.Loading
                try {
                    val issues = repository.issues(owner, repo, _filter.value).cachedIn(viewModelScope)
                    _uiState.value = IssueListUiState.Success(issues = issues)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = IssueListUiState.Error(errorType = e.toIssueErrorType())
                }
            }
        }
    }
