@file:Suppress("TooGenericExceptionCaught")
// 数据流构造期异常统一归一化（同 NotificationsViewModel 先例）

package com.yumiru11.githubapp.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.feature.search.data.SearchHistoryRepository
import com.yumiru11.githubapp.feature.search.data.SearchPagingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索页 ViewModel（T18）。
 *
 * - 输入防抖：键入 300ms 后自动搜索（docs/ui-design.md §3.3），不记历史；
 *   提交（IME 搜索键/历史 chip/qualifier chip）立即搜索并记历史
 * - 结果 Tab 单独 Paging：只构建活动 Tab 的流（搜索 API 限流严格，避免多路并行）
 * - 代码搜索登录门：未登录不发起请求，UI 展示登录引导；登录后自动补搜
 * - 429/网络错误：数据流构造期异常 → [SearchUiState.Error]（Paging 期错误由
 *   PagingSource 归一化后经 loadState 呈现）
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val pagingRepository: SearchPagingRepository,
        private val historyRepository: SearchHistoryRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        /** 输入框原文（驱动历史/建议 UI 与防抖搜索） */
        private val _input = MutableStateFlow("")
        val input: StateFlow<String> = _input.asStateFlow()

        /** 已提交的搜索词（trim 后） */
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        private val _activeTab = MutableStateFlow(SearchTab.REPOSITORIES)
        val activeTab: StateFlow<SearchTab> = _activeTab.asStateFlow()

        /** 登录态（代码搜索门） */
        private val _isLoggedIn = MutableStateFlow(false)
        val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

        private val _history = MutableStateFlow<List<String>>(emptyList())
        val history: StateFlow<List<String>> = _history.asStateFlow()

        private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                sessionManager.authState.collect { auth ->
                    val loggedIn = auth is AuthState.SignedIn || auth is AuthState.PAT
                    val becameLoggedIn = !_isLoggedIn.value && loggedIn
                    _isLoggedIn.value = loggedIn
                    // 登录后自动补搜当前查询的代码 Tab（未登录时被门拦下的场景）
                    if (becameLoggedIn && _activeTab.value == SearchTab.CODE && _query.value.isNotEmpty()) {
                        load(_query.value, SearchTab.CODE)
                    }
                }
            }

            viewModelScope.launch {
                _history.value = historyRepository.recent()
            }

            viewModelScope.launch {
                _input
                    .debounce(DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { text -> onQueryChanged(text) }
            }

            viewModelScope.launch {
                _activeTab.collect { tab ->
                    val current = _query.value
                    if (current.isNotEmpty() && _uiState.value is SearchUiState.Success) {
                        load(current, tab)
                    }
                }
            }
        }

        /** 键入更新：非空走 300ms 防抖；清空立即回 Idle（历史/建议页，不等防抖） */
        fun onQueryChange(text: String) {
            if (text.isBlank()) {
                onQueryChanged("")
            }
            _input.value = text
        }

        /** 提交搜索（IME 搜索键/历史 chip/qualifier chip）：立即搜索 + 记历史 */
        fun submitQuery(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            _input.value = trimmed
            recordHistory(trimmed)
            if (trimmed != _query.value) {
                _query.value = trimmed
                viewModelScope.launch { load(trimmed, _activeTab.value) }
            }
        }

        /** 切换结果 Tab（重建该 Tab 的 Paging 流；_activeTab 收集器负责实际加载） */
        fun selectTab(tab: SearchTab) {
            if (tab != _activeTab.value) {
                _activeTab.value = tab
            }
        }

        /** 清空搜索历史 */
        fun clearHistory() {
            viewModelScope.launch {
                historyRepository.clear()
                _history.value = emptyList()
            }
        }

        /** 错误态重试 */
        fun retry() {
            val current = _query.value
            if (current.isNotEmpty() && _uiState.value is SearchUiState.Error) {
                viewModelScope.launch { load(current, _activeTab.value) }
            }
        }

        private fun onQueryChanged(text: String) {
            val trimmed = text.trim()
            if (trimmed == _query.value) return
            if (trimmed.isEmpty()) {
                _query.value = ""
                _uiState.value = SearchUiState.Idle
                return
            }
            _query.value = trimmed
            viewModelScope.launch { load(trimmed, _activeTab.value) }
        }

        private fun recordHistory(query: String) {
            viewModelScope.launch {
                historyRepository.add(query)
                _history.value = historyRepository.recent()
            }
        }

        private suspend fun load(
            query: String,
            tab: SearchTab,
        ) {
            _uiState.value = SearchUiState.Loading
            if (tab == SearchTab.CODE && !_isLoggedIn.value) {
                // 代码搜索需登录：不发起请求，UI 按 isLoggedIn 展示登录引导
                _uiState.value = emptySuccess(query, tab)
                return
            }
            try {
                val flows = flowsFor(tab, query)
                _uiState.value =
                    SearchUiState.Success(
                        query = query,
                        activeTab = tab,
                        repositories = flows.repositories,
                        users = flows.users,
                        issues = flows.issues,
                        pullRequests = flows.pullRequests,
                        code = flows.code,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.toSearchErrorType())
            }
        }

        /** 只构建活动 Tab 的真实流；其余 Tab 空流（活动流切换时按需重建） */
        private suspend fun flowsFor(
            tab: SearchTab,
            query: String,
        ): TabFlows =
            when (tab) {
                SearchTab.REPOSITORIES -> {
                    TabFlows(
                        repositories = pagingRepository.repositories(query).cachedIn(viewModelScope),
                    )
                }

                SearchTab.USERS -> {
                    TabFlows(
                        users = pagingRepository.users(query).cachedIn(viewModelScope),
                    )
                }

                SearchTab.ISSUES -> {
                    TabFlows(
                        issues = pagingRepository.issues(query).cachedIn(viewModelScope),
                    )
                }

                SearchTab.PULL_REQUESTS -> {
                    TabFlows(
                        pullRequests = pagingRepository.pullRequests(query).cachedIn(viewModelScope),
                    )
                }

                SearchTab.CODE -> {
                    TabFlows(
                        code = pagingRepository.code(query).cachedIn(viewModelScope),
                    )
                }
            }

        private fun emptySuccess(
            query: String,
            tab: SearchTab,
        ): SearchUiState.Success =
            SearchUiState.Success(
                query = query,
                activeTab = tab,
                repositories = flowOf(PagingData.empty()),
                users = flowOf(PagingData.empty()),
                issues = flowOf(PagingData.empty()),
                pullRequests = flowOf(PagingData.empty()),
                code = flowOf(PagingData.empty()),
            )

        private data class TabFlows(
            val repositories: Flow<PagingData<Repository>> = flowOf(PagingData.empty()),
            val users: Flow<PagingData<User>> = flowOf(PagingData.empty()),
            val issues: Flow<PagingData<SearchIssue>> = flowOf(PagingData.empty()),
            val pullRequests: Flow<PagingData<SearchIssue>> = flowOf(PagingData.empty()),
            val code: Flow<PagingData<SearchCodeItem>> = flowOf(PagingData.empty()),
        )

        private companion object {
            const val DEBOUNCE_MS = 300L
        }
    }
