package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestWriteAction
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission

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
        /** T17：当前会话权限（REST 仓库 permissions 映射；UNKNOWN = 游客/缺失 → 隐藏写入口） */
        val viewerPermission: ViewerPermission = ViewerPermission.UNKNOWN,
        /** T17：仓库默认分支（删除分支候选判断） */
        val defaultBranch: String? = null,
        /** T17：可发起 Review（READ 及以上） */
        val canReview: Boolean = false,
        /** T17：可 approve / request changes（WRITE） */
        val canApprove: Boolean = false,
        /** T17：可合并（WRITE 且 PR 打开；按钮另有 mergeable 约束） */
        val canMerge: Boolean = false,
        /** T17：head 与 base 同仓库（Update branch / 删除分支可用前提） */
        val headSameRepo: Boolean = false,
        /** T17：head == 默认分支（默认分支不可删） */
        val headIsDefaultBranch: Boolean = false,
        /** T17：head 分支可删（WRITE + 同仓库 + 非默认 + 未删过） */
        val canDeleteHeadBranch: Boolean = false,
        /** T17：进行中的写操作（防重入；按钮 loading/禁用） */
        val pendingAction: PullRequestWriteAction? = null,
    ) : PullRequestDetailUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射，ViewModel 不产英文） */
    data class Error(
        val errorType: PullRequestErrorType,
    ) : PullRequestDetailUiState
}

/** PR 详情页一次性事件（T16/T17 写操作结果 Snackbar；UI 层 stringResource 本地化） */
sealed interface PullRequestDetailEvent {
    /** 行评论新增/回复/会话解析写操作失败 */
    data object CommentFailed : PullRequestDetailEvent

    /** T17：Review 提交失败（已回滚） */
    data object ReviewFailed : PullRequestDetailEvent

    /** T17：合并成功（已乐观置 MERGED 并刷新） */
    data object MergeSucceeded : PullRequestDetailEvent

    /** T17：合并失败（已回滚） */
    data object MergeFailed : PullRequestDetailEvent

    /** T17：Update branch 成功/失败 */
    data object UpdateBranchSucceeded : PullRequestDetailEvent

    data object UpdateBranchFailed : PullRequestDetailEvent

    /** T17：删除分支成功/失败（删除失败不回滚合并） */
    data object DeleteBranchSucceeded : PullRequestDetailEvent

    data object DeleteBranchFailed : PullRequestDetailEvent
}
