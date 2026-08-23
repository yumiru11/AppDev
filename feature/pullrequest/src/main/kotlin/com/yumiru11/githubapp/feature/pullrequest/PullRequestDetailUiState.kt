package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread

/**
 * PR 详情页 UI 状态（T15）。
 *
 * 详情 + 时间线 + 提交 + 文件 + Checks 一次性并行加载（all-or-nothing：
 * 任一失败 → [Error]，不产部分 Success）。
 */
sealed interface PullRequestDetailUiState {
    /** 加载中 */
    data object Loading : PullRequestDetailUiState

    /** 加载成功（PR 详情 + 四 Tab 数据） */
    data class Success(
        val pullRequest: PullRequest,
        val timeline: List<PullRequestTimelineItem>,
        val commits: List<PullRequestCommit>,
        val files: List<PullRequestFile>,
        val checkRuns: List<CheckRun>,
        val combinedStatus: CombinedStatus?,
        /** T16：行内评论（Files changed 锚点展示） */
        val reviewComments: List<ReviewComment> = emptyList(),
        /** T16：会话（解析状态） */
        val reviewThreads: List<ReviewThread> = emptyList(),
        /** T16：GraphQL 可用（== 可解析会话）；REST-only 会话隐藏解析入口 */
        val canResolveThreads: Boolean = false,
    ) : PullRequestDetailUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射，ViewModel 不产英文） */
    data class Error(
        val errorType: PullRequestErrorType,
    ) : PullRequestDetailUiState
}

/** PR 详情页一次性事件（T16 写操作失败 Snackbar；UI 层 stringResource 本地化） */
sealed interface PullRequestDetailEvent {
    /** 行评论新增/回复/会话解析写操作失败 */
    data object CommentFailed : PullRequestDetailEvent
}
