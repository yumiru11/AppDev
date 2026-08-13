package com.yumiru11.githubapp.feature.issue

import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import kotlinx.coroutines.flow.Flow

/**
 * Issue 列表 UI 状态（T13）。
 *
 * - [Success] 内嵌 Paging 数据流；分页加载错误由 UI 层 LazyPagingItems.loadState
 *   呈现与重试（Paging 拥有加载生命周期，不进 VM 状态机）
 * - [Error] 为 VM 层可捕获的失败（数据流构造期异常）
 */
sealed interface IssueListUiState {
    /** 加载中 */
    data object Loading : IssueListUiState

    /** 加载成功（[issues] 分页数据流；当前过滤态由 [IssueListViewModel.filter] 单独暴露） */
    data class Success(
        val issues: Flow<PagingData<Issue>>,
    ) : IssueListUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: IssueErrorType,
    ) : IssueListUiState
}
