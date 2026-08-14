package com.yumiru11.githubapp.feature.notifications

import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import kotlinx.coroutines.flow.Flow

/**
 * 通知页 UI 状态。
 *
 * - [Success] 内嵌 Paging 数据流；分页加载错误由 UI 层 LazyPagingItems.loadState
 *   呈现与重试（Paging 拥有加载生命周期，不进 VM 状态机）
 * - [Error] 为 VM 层可捕获的失败（数据流构造期异常；正常路径由 PagingSource 消化）
 */
sealed interface NotificationsUiState {
    /** 加载中 */
    data object Loading : NotificationsUiState

    /** 未登录：明确引导登录（复用 T4 auth 状态驱动） */
    data object Unauthenticated : NotificationsUiState

    /** 加载成功（[filter] 当前过滤；[notifications] 分页数据流） */
    data class Success(
        val filter: NotificationFilter,
        val notifications: Flow<PagingData<NotificationItem>>,
    ) : NotificationsUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: NotificationsErrorType,
    ) : NotificationsUiState
}

/**
 * 通知页加载错误类型。
 */
enum class NotificationsErrorType {
    /** 网络/IO 错误 */
    NETWORK,

    /** 凭据无效/过期（401/403，与网络错误区分） */
    UNAUTHORIZED,

    /** 其他未知错误 */
    UNKNOWN,
}
