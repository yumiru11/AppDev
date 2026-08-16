@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：数据流构造期异常统一兜底为 Error 态（同 RepoDetailViewModel 先例）
// - SwallowedException：已读写操作失败静默（T19 无错误 UI 通道；Snackbar 事件通道为 T24 写功能票范围）

package com.yumiru11.githubapp.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.feature.notifications.data.NotificationRepository
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 通知页 ViewModel（T19）。
 *
 * - 登录态（T4 auth 状态）：[AuthState.Anonymous] → 未登录引导；SignedIn/PAT → 加载列表
 * - 过滤切换：[selectFilter] 重建 Paging 数据流（cachedIn 避免重复分页循环）
 * - 已读操作：[markRead]/[markAllRead] 委托仓库（仓库成功 PATCH 后自动刷新列表）。
 *   失败静默（Snackbar 事件通道为 T24 写功能票范围，T19 保持最小）
 */
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val notificationRepository: NotificationRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        private val _filter = MutableStateFlow(NotificationFilter.ALL)
        val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

        private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
        val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                sessionManager.authState.collect { auth ->
                    when (auth) {
                        is AuthState.Anonymous -> {
                            _uiState.value = NotificationsUiState.Unauthenticated
                        }

                        is AuthState.SignedIn, is AuthState.PAT -> {
                            load(_filter.value)
                        }
                    }
                }
            }

            viewModelScope.launch {
                _filter.drop(1).collect { newFilter ->
                    if (_uiState.value is NotificationsUiState.Success) {
                        load(newFilter)
                    }
                }
            }
        }

        /** 切换过滤（全部/参与/提及） */
        fun selectFilter(filter: NotificationFilter) {
            _filter.value = filter
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is NotificationsUiState.Error) {
                load(_filter.value)
            }
        }

        /** 标记单条已读 */
        fun markRead(item: NotificationItem) {
            viewModelScope.launch {
                runCatching { notificationRepository.markRead(item.id) }
            }
        }

        /** 标记全部已读 */
        fun markAllRead() {
            viewModelScope.launch {
                runCatching { notificationRepository.markAllRead() }
            }
        }

        private fun load(filter: NotificationFilter) {
            val notifications: Flow<PagingData<NotificationItem>> =
                try {
                    notificationRepository.notifications(filter).cachedIn(viewModelScope)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = NotificationsUiState.Error(errorType = e.toNotificationsErrorType())
                    return
                }
            _uiState.value = NotificationsUiState.Success(filter = filter, notifications = notifications)
        }
    }

/**
 * 异常 → [NotificationsErrorType] 映射（T19 补全：401/403 凭据失效与网络错误区分，消除 UNAUTHORIZED/NETWORK 不可达）。
 *
 * 规则：401/403 → UNAUTHORIZED；其余 HttpException 与 IOException → NETWORK；其余 → UNKNOWN。
 * ViewModel/UI 只产类型，不产英文文案（文案由 UI 层 stringResource 本地化）。
 */
internal fun Throwable.toNotificationsErrorType(): NotificationsErrorType =
    when {
        this is HttpException && (code() == 401 || code() == 403) -> NotificationsErrorType.UNAUTHORIZED
        this is HttpException || this is IOException -> NotificationsErrorType.NETWORK
        else -> NotificationsErrorType.UNKNOWN
    }
