package com.yumiru11.githubapp.feature.issue

import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem

/**
 * Issue 详情页 UI 状态。
 */
sealed interface IssueDetailUiState {
    /** 加载中 */
    data object Loading : IssueDetailUiState

    /** 加载成功（Issue 详情 + 时间线） */
    data class Success(
        val issue: Issue,
        val timeline: List<IssueTimelineItem>,
    ) : IssueDetailUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射，ViewModel 不产英文） */
    data class Error(
        val errorType: IssueErrorType,
    ) : IssueDetailUiState
}
