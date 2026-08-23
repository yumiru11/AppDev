@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：快照加载异常统一兜底为 Error 态（同 T19 ViewModel 先例）
// - SwallowedException：已读写失败按 T19 约定静默（Snackbar 事件通道为 T24 范围）；
//   删除失败例外——重拉快照对齐服务端真相（见 [delete]）

package com.yumiru11.githubapp.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.feature.notifications.data.NotificationRepository
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import com.yumiru11.githubapp.feature.notifications.model.groupByRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知面板 ViewModel（#88）：一次性快照 + 分组折叠 + 写操作乐观更新。
 *
 * - 登录态驱动加载（T4 auth：Anonymous → 引导空态；SignedIn/PAT → 拉快照）
 * - 右滑已读 / 全部已读：先本地置已读（色条淡出 + 条目重排），PATCH 失败静默（T19 约定）
 * - 左滑删除：先本地移除（组空整组消失，animateItem 补位），DELETE 失败重拉快照回滚视觉
 * - 过滤切换：重置折叠态并重拉快照
 */
@HiltViewModel
class NotificationsPanelViewModel
    @Inject
    constructor(
        private val notificationRepository: NotificationRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        private val _filter = MutableStateFlow(NotificationFilter.ALL)
        val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

        private val _uiState = MutableStateFlow<NotificationsPanelUiState>(NotificationsPanelUiState.Loading)
        val uiState: StateFlow<NotificationsPanelUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                sessionManager.authState.collect { auth ->
                    when (auth) {
                        is AuthState.Anonymous -> _uiState.value = NotificationsPanelUiState.Unauthenticated
                        is AuthState.SignedIn, is AuthState.PAT -> load(_filter.value)
                    }
                }
            }
        }

        /** 切换过滤（全部/参与/提及）：重置折叠态并重拉快照 */
        fun selectFilter(filter: NotificationFilter) {
            if (_filter.value == filter) return
            _filter.value = filter
            if (_uiState.value !is NotificationsPanelUiState.Unauthenticated) {
                load(filter)
            }
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is NotificationsPanelUiState.Error) {
                load(_filter.value)
            }
        }

        /** 展开/折叠仓库分组 */
        fun toggleGroup(repoFullName: String) {
            mapSuccess { state ->
                state.copy(
                    collapsedRepos =
                        if (repoFullName in state.collapsedRepos) {
                            state.collapsedRepos - repoFullName
                        } else {
                            state.collapsedRepos + repoFullName
                        },
                )
            }
        }

        /** 右滑标已读：乐观置已读（行保留、色条淡出），PATCH 失败静默 */
        fun markRead(item: NotificationItem) {
            updateItem(item.id) { it.copy(unread = false) }
            viewModelScope.launch {
                runCatching { notificationRepository.markRead(item.id) }
            }
        }

        /** 全部已读：乐观清零全部未读，PATCH 失败静默 */
        fun markAllRead() {
            mapSuccess { state ->
                state.copy(
                    groups =
                        state.groups.map { group ->
                            group.copy(items = group.items.map { it.copy(unread = false) })
                        },
                )
            }
            viewModelScope.launch {
                runCatching { notificationRepository.markAllRead() }
            }
        }

        /** 左滑删除：乐观移除条目（组空则整组消失），DELETE 失败重拉快照对齐服务端真相 */
        fun delete(item: NotificationItem) {
            mapSuccess { state ->
                state.copy(
                    groups =
                        state.groups.mapNotNull { group ->
                            val remaining = group.items.filterNot { it.id == item.id }
                            if (remaining.isEmpty()) null else group.copy(items = remaining)
                        },
                )
            }
            viewModelScope.launch {
                runCatching { notificationRepository.markDone(item.id) }
                    .onFailure { exception ->
                        if (exception is CancellationException) throw exception
                        load(_filter.value)
                    }
            }
        }

        private fun load(filter: NotificationFilter) {
            viewModelScope.launch {
                try {
                    val items = notificationRepository.latest(filter)
                    _uiState.value =
                        NotificationsPanelUiState.Success(
                            filter = filter,
                            groups = groupByRepository(items),
                            // 过滤切换语义为「新视图」：不携带旧视图折叠态
                            collapsedRepos = emptySet(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = NotificationsPanelUiState.Error(e.toNotificationsErrorType())
                }
            }
        }

        private inline fun mapSuccess(transform: (NotificationsPanelUiState.Success) -> NotificationsPanelUiState.Success) {
            val current = _uiState.value as? NotificationsPanelUiState.Success ?: return
            _uiState.value = transform(current)
        }

        private fun updateItem(
            id: String,
            transform: (NotificationItem) -> NotificationItem,
        ) {
            mapSuccess { state ->
                state.copy(
                    groups =
                        state.groups.map { group ->
                            if (group.items.none { it.id == id }) {
                                group
                            } else {
                                group.copy(items = group.items.map { if (it.id == id) transform(it) else it })
                            }
                        },
                )
            }
        }
    }
