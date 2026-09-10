@file:Suppress("TooGenericExceptionCaught")
// 登录用户获取/数据流构造期异常统一兜底为 Error 态（同 NotificationsViewModel 先例）

package com.yumiru11.githubapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.feature.home.data.FeedRepository
import com.yumiru11.githubapp.feature.home.data.TrendRepository
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

        /** 异常 → 错误类型（IO/HTTP → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): HomeErrorType =
            if (e is IOException || e is HttpException) HomeErrorType.NETWORK else HomeErrorType.UNKNOWN
    }
