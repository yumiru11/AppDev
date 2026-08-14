@file:Suppress("TooGenericExceptionCaught")
// 登录用户获取/数据流构造期异常统一兜底为 Error 态（同 NotificationsViewModel 先例）

package com.yumiru11.githubapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.feature.home.data.FeedRepository
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
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = HomeUiState.Error(errorType = mapError(e))
                }
            }
        }

        /** 异常 → 错误类型（IO/HTTP → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): HomeErrorType =
            if (e is IOException || e is HttpException) HomeErrorType.NETWORK else HomeErrorType.UNKNOWN
    }
