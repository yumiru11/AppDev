package com.yumiru11.githubapp.feature.issue

import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext

/**
 * Issue 详情页 UI 状态。
 */
sealed interface IssueDetailUiState {
    /** 加载中 */
    data object Loading : IssueDetailUiState

    /** 加载成功（Issue 详情 + 时间线 + 写操作上下文） */
    data class Success(
        val issue: Issue,
        val timeline: List<IssueTimelineItem>,
        /** 写操作上下文（viewer login/权限/node id）；null = 未获取，写操作隐藏 */
        val writeContext: IssueWriteContext? = null,
        /** 当前 viewer 已添加的反应（itemId → content → reactionId），供 toggle 与删除 */
        val myReactions: Map<Long, Map<String, Long>> = emptyMap(),
    ) : IssueDetailUiState {
        /** 是否可评论（已登录即可） */
        val canComment: Boolean
            get() = writeContext?.viewerLogin != null

        /** 是否可编辑 Issue title/body（作者本人或 WRITE+） */
        val canEditIssue: Boolean
            get() = isAuthor || canWrite

        /** 是否可编辑 Labels/Assignees/Milestone（TRIAGE+） */
        val canManageMeta: Boolean
            get() = (writeContext?.viewerPermission?.level ?: IssueViewerPermission.NONE.level) >= IssueViewerPermission.TRIAGE.level

        /** 是否可关闭/重开（作者本人或 WRITE+） */
        val canCloseReopen: Boolean
            get() = isAuthor || canWrite

        /** 是否可编辑/删除某条评论（仅评论作者本人） */
        fun canEditComment(comment: IssueTimelineItem.Comment): Boolean =
            writeContext?.viewerLogin != null && writeContext.viewerLogin == comment.author?.login

        private val isAuthor: Boolean
            get() = writeContext?.viewerLogin != null && writeContext.viewerLogin == issue.author?.login

        private val canWrite: Boolean
            get() = (writeContext?.viewerPermission?.level ?: IssueViewerPermission.NONE.level) >= IssueViewerPermission.WRITE.level
    }

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射，ViewModel 不产英文） */
    data class Error(
        val errorType: IssueErrorType,
    ) : IssueDetailUiState
}

/**
 * 详情页事件通道（T14 写操作反馈）。
 *
 * ViewModel 只产类型不产文案；UI 层将 [IssueSnackbarMessage] 映射为 stringResource。
 */
sealed interface IssueDetailEvent {
    data class ShowSnackbar(
        val message: IssueSnackbarMessage,
    ) : IssueDetailEvent
}

/** Snackbar 文案类型（成功反馈 + 写失败错误规整） */
enum class IssueSnackbarMessage {
    COMMENT_ADDED,
    COMMENT_UPDATED,
    COMMENT_DELETED,
    REACTION_ADDED,
    REACTION_REMOVED,
    ISSUE_CLOSED,
    ISSUE_REOPENED,
    ISSUE_UPDATED,
    TASK_LIST_UPDATED,
    ERROR_NETWORK,
    ERROR_FORBIDDEN,
    ERROR_NOT_FOUND,
    ERROR_VALIDATION,
    ERROR_UNKNOWN,
}
