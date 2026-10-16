@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// 登录用户获取/数据流构造期异常统一兜底为 Error 态（同 NotificationsViewModel 先例）；
// 异常对象本身不再外传（UI 只按 Error 态渲染重试），故 SwallowedException 一并静音

package com.yumiru11.githubapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.feature.home.data.FeedRepository
import com.yumiru11.githubapp.feature.home.data.HomeSectionsRepository
import com.yumiru11.githubapp.feature.home.data.TrendRepository
import com.yumiru11.githubapp.feature.home.model.HomeDiscussionItem
import com.yumiru11.githubapp.feature.home.model.HomeIssueFilter
import com.yumiru11.githubapp.feature.home.model.HomeIssueItem
import com.yumiru11.githubapp.feature.home.model.HomePullFilter
import com.yumiru11.githubapp.feature.home.model.HomeUserItem
import com.yumiru11.githubapp.feature.home.model.TrendItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 首页动态流 ViewModel（T10）。
 *
 * - 登录态（T4 auth 状态）：[AuthState.Anonymous] → 未登录引导；SignedIn/PAT → 加载动态流
 * - 加载流程：先取当前用户 login（GET /user），再构造 received_events 分页流
 *   （login 获取失败 → Error 态；分页加载错误由 UI 层 loadState 呈现）
 * - 下拉刷新在 UI 层走 LazyPagingItems.refresh()，不经 VM
 * - Trending（L08）：独立 StateFlow；数据层永不抛出，失败即空列表 → UI 静默隐藏小节
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val trendRepository: TrendRepository,
        private val sectionsRepository: HomeSectionsRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        /** feed 尾部 Trending 小节数据（L08）：空列表 = 静默隐藏（不展示错误块） */
        private val _trending = MutableStateFlow<List<TrendItem>>(emptyList())
        val trending: StateFlow<List<TrendItem>> = _trending.asStateFlow()

        init {
            viewModelScope.launch {
                sessionManager.authState.collect { auth ->
                    when (auth) {
                        is AuthState.Anonymous -> {
                            _uiState.value = HomeUiState.Unauthenticated
                        }

                        is AuthState.SignedIn, is AuthState.PAT -> {
                            load()
                        }
                    }
                }
            }
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is HomeUiState.Error) {
                load()
            }
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = HomeUiState.Loading
                try {
                    val login = feedRepository.currentLogin()
                    _uiState.value = HomeUiState.Success(feed = feedRepository.feed(login).cachedIn(viewModelScope))
                    loadTrending()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = HomeUiState.Error(errorType = mapError(e))
                }
            }
        }

        /**
         * Trending 小节独立加载（L08）：不参与 feed 状态机。
         *
         * 数据层契约「永不抛出」，故失败静默为已有值（首轮失败 = 空列表 → 小节不渲染），
         * 绝不把首页推进 Error 态（加分内容不得打断 feed）。
         */
        private fun loadTrending() {
            viewModelScope.launch {
                _trending.value = trendRepository.trending()
            }
        }

        // ── 内容分区（2026-10 UI 重构：议题 / 拉取请求 / 讨论 / 发现）────────────
        // 每分区独立状态：一个分区失败不影响其它分区，也不影响动态流。

        private val _issues = MutableStateFlow<HomeSectionState<HomeIssueItem>>(HomeSectionState.Loading)
        val issues: StateFlow<HomeSectionState<HomeIssueItem>> = _issues.asStateFlow()

        private val _pulls = MutableStateFlow<HomeSectionState<HomeIssueItem>>(HomeSectionState.Loading)
        val pulls: StateFlow<HomeSectionState<HomeIssueItem>> = _pulls.asStateFlow()

        private val _discussions = MutableStateFlow<HomeSectionState<HomeDiscussionItem>>(HomeSectionState.Loading)
        val discussions: StateFlow<HomeSectionState<HomeDiscussionItem>> = _discussions.asStateFlow()

        private val _suggestedUsers = MutableStateFlow<HomeSectionState<HomeUserItem>>(HomeSectionState.Loading)
        val suggestedUsers: StateFlow<HomeSectionState<HomeUserItem>> = _suggestedUsers.asStateFlow()

        private val _hotIssues = MutableStateFlow<HomeSectionState<HomeIssueItem>>(HomeSectionState.Loading)
        val hotIssues: StateFlow<HomeSectionState<HomeIssueItem>> = _hotIssues.asStateFlow()

        private var issueFilter: HomeIssueFilter = HomeIssueFilter.ASSIGNED
        private var pullFilter: HomePullFilter = HomePullFilter.INVOLVED

        /** 已触发过首载的分区（进入即加载一次，返回分区不重复打搜索配额）。 */
        private val startedSections = mutableSetOf<HomeTab>()

        /**
         * 分区首次可见时触发首载（幂等）：由 UI 在 LaunchedEffect(selectedTab) 调用。
         *
         * 为什么幂等放在 VM：搜索 API 配额紧（30 次/分），左右滑来回切分区不能反复请求。
         */
        fun ensureSectionStarted(tab: HomeTab) {
            if (!startedSections.add(tab)) return
            when (tab) {
                HomeTab.FEED -> Unit
                HomeTab.ISSUES -> loadIssues()
                HomeTab.PULL_REQUESTS -> loadPulls()
                HomeTab.DISCUSSIONS -> loadDiscussions()
                HomeTab.EXPLORE -> loadExplore()
            }
        }

        /** 议题子过滤（assigned/created/mentioned）立即重载 */
        fun selectIssueFilter(filter: HomeIssueFilter) {
            if (issueFilter == filter) return
            issueFilter = filter
            loadIssues()
        }

        /** 拉取请求子过滤（involved/review/recent）立即重载 */
        fun selectPullFilter(filter: HomePullFilter) {
            if (pullFilter == filter) return
            pullFilter = filter
            loadPulls()
        }

        fun retryIssues() = loadIssues()

        fun retryPulls() = loadPulls()

        fun retryDiscussions() = loadDiscussions()

        fun retryExplore() = loadExplore()

        private fun loadIssues() {
            viewModelScope.launch {
                _issues.value = HomeSectionState.Loading
                _issues.value =
                    sectionResult { sectionsRepository.issues(issueFilter) }
            }
        }

        private fun loadPulls() {
            viewModelScope.launch {
                _pulls.value = HomeSectionState.Loading
                _pulls.value =
                    sectionResult { sectionsRepository.pulls(pullFilter) }
            }
        }

        private fun loadDiscussions() {
            viewModelScope.launch {
                _discussions.value = HomeSectionState.Loading
                _discussions.value =
                    sectionResult { sectionsRepository.discussions() }
            }
        }

        private fun loadExplore() {
            viewModelScope.launch {
                _suggestedUsers.value = HomeSectionState.Loading
                _hotIssues.value = HomeSectionState.Loading
                // 两个二级列表各自独立成败：热门议题失败不应该清空建议用户
                _suggestedUsers.value =
                    sectionResult { sectionsRepository.suggestedUsers() }
                _hotIssues.value =
                    sectionResult { sectionsRepository.hotIssues() }
                if (_trending.value.isEmpty()) loadTrending()
            }
        }

        /** 分区加载统一兜底：失败 → [HomeSectionState.Error]（协程取消必须原样抛出）。 */
        private suspend fun <T> sectionResult(block: suspend () -> List<T>): HomeSectionState<T> =
            try {
                HomeSectionState.Data(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeSectionState.Error
            }

        /** 异常 → 错误类型（IO/HTTP → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): HomeErrorType =
            if (e is IOException || e is HttpException) HomeErrorType.NETWORK else HomeErrorType.UNKNOWN
    }
