package com.yumiru11.githubapp.feature.issue

import androidx.compose.runtime.Immutable
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueMilestone
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
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
        /** #163 L01：viewer 是否订阅本 Issue（未登录/探测失败 → false） */
        val isSubscribed: Boolean = false,
        /** #163 L01：订阅写操作进行中（按钮 pending 防连点） */
        val subscriptionPending: Boolean = false,
    ) : IssueDetailUiState {
        /** 是否可评论（已登录即可） */
        val canComment: Boolean
            get() = writeContext?.viewerLogin != null

        /** 是否可编辑 Issue title/body（作者本人或 WRITE+） */
        val canEditIssue: Boolean
            get() = isAuthor || canWrite

        /** 是否可编辑 Labels/Assignees/Milestone（TRIAGE+；含 WRITE/MAINTAIN/ADMIN） */
        val canManageMeta: Boolean
            get() = (writeContext?.viewerPermission?.level ?: IssueViewerPermission.NONE.level) >= IssueViewerPermission.TRIAGE.level

        /** #163 L01：是否可订阅/取消订阅（Issue 级订阅需登录态） */
        val canSubscribe: Boolean
            get() = writeContext?.viewerLogin != null

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
 * Issue 元数据编辑 Sheet 状态（#163 L02：Labels 多选 / Assignees 多选 / Milestone 单选）。
 *
 * 只读端点加载失败 → [errorType]（Sheet 内提示重试），不阻塞详情页。
 * 保存走乐观更新：调用方（ViewModel）先把选择集合并入 HeaderCard，再持久化，失败回滚。
 */
@Immutable
data class IssueEditUiState(
    /** 仓库标签候选项（[selectedLabels] 存名称，PATCH labels 字段要求标签名） */
    val labels: List<IssueLabel> = emptyList(),
    /** 可指派用户候选项（[selectedAssignees] 存 login） */
    val assignees: List<IssueUser> = emptyList(),
    /** 里程碑候选项（[selectedMilestone] 存编号，null = 无里程碑） */
    val milestones: List<IssueMilestone> = emptyList(),
    val selectedLabels: Set<String> = emptySet(),
    val selectedAssignees: Set<String> = emptySet(),
    val selectedMilestone: Long? = null,
    /** 候选项加载中（首帧骨架/进度） */
    val loading: Boolean = true,
    /** 候选项加载失败（非空即失败态，UI 显示重试） */
    val errorType: IssueErrorType? = null,
    /** 保存写操作进行中（按钮 pending 防连点） */
    val saving: Boolean = false,
) {
    /** 标签多选切换（纯函数，便于单测） */
    fun toggleLabel(name: String): IssueEditUiState = copy(selectedLabels = selectedLabels.toggle(name))

    /** Assignee 多选切换（纯函数） */
    fun toggleAssignee(login: String): IssueEditUiState = copy(selectedAssignees = selectedAssignees.toggle(login))

    /** Milestone 单选（再次点击已选项 = 取消选择 → 清除里程碑） */
    fun selectMilestone(number: Long?): IssueEditUiState =
        copy(selectedMilestone = if (number != null && number == selectedMilestone) null else number)

    private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value
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

    /** #163 L01：订阅成功 */
    SUBSCRIBED,

    /** #163 L01：取消订阅成功 */
    UNSUBSCRIBED,

    /** #163 L02：Labels/Assignees/Milestone 保存成功 */
    ISSUE_META_UPDATED,
    ERROR_NETWORK,
    ERROR_FORBIDDEN,
    ERROR_NOT_FOUND,
    ERROR_VALIDATION,
    ERROR_UNKNOWN,
}
