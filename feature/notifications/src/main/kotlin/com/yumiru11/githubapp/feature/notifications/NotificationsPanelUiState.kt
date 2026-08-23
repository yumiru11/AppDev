package com.yumiru11.githubapp.feature.notifications

import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationGroup

/**
 * 通知面板 UI 状态（#88，ui-design §3.4 完整形态）。
 *
 * - [Success] 携带分组快照与折叠集合；写操作（已读/done）乐观更新本地分组，
 *   删除失败时 ViewModel 重拉快照对齐服务端
 * - [Error] 为快照拉取失败（类型驱动 UI 层本地化文案）
 */
sealed interface NotificationsPanelUiState {
    /** 快照加载中 */
    data object Loading : NotificationsPanelUiState

    /** 未登录：登录引导空态（T4 auth 状态驱动） */
    data object Unauthenticated : NotificationsPanelUiState

    /**
     * 加载成功。
     *
     * @param filter 当前过滤（全部/参与/提及）
     * @param groups 按仓库分组快照（[groupByRepository] 产物）
     * @param collapsedRepos 已折叠仓库名集合（UI 折叠态，不参与排序）
     */
    data class Success(
        val filter: NotificationFilter,
        val groups: List<NotificationGroup>,
        val collapsedRepos: Set<String> = emptySet(),
    ) : NotificationsPanelUiState

    /** 加载失败 */
    data class Error(
        val errorType: NotificationsErrorType,
    ) : NotificationsPanelUiState
}
