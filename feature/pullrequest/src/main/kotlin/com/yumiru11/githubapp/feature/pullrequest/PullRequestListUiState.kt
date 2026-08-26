package com.yumiru11.githubapp.feature.pullrequest

import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import kotlinx.coroutines.flow.Flow

/**
 * PR 列表 UI 状态（T15）。
 *
 * - [Success] 内嵌 Paging 数据流；分页加载错误由 UI 层 LazyPagingItems.loadState
 *   呈现与重试（Paging 拥有加载生命周期，不进 VM 状态机）
 * - [Error] 为 VM 层可捕获的失败（数据流构造期异常）
 */
sealed interface PullRequestListUiState {
    /** 加载中 */
    data object Loading : PullRequestListUiState

    /** 加载成功（[pulls] 分页数据流；当前过滤态由 [PullRequestListViewModel.filter] 单独暴露） */
    data class Success(
        val pulls: Flow<PagingData<PullRequest>>,
        /** T23：当前会话有推送权限（创建 PR 按钮显隐；加载失败保守隐藏） */
        val canCreatePullRequest: Boolean = false,
    ) : PullRequestListUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: PullRequestErrorType,
    ) : PullRequestListUiState
}
