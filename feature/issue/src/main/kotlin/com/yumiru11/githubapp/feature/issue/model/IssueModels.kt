package com.yumiru11.githubapp.feature.issue.model

import androidx.compose.runtime.Immutable

/** Issue 状态（GitHub REST state 字段映射） */
enum class IssueState {
    OPEN,
    CLOSED,

    ;

    companion object {
        fun fromRaw(value: String): IssueState = if (value == "open") OPEN else CLOSED
    }

    /** REST state 字段值（写操作 PATCH 用） */
    fun toRaw(): String = if (this == OPEN) "open" else "closed"
}

/** Issue 列表/详情条目 */
@Immutable
data class Issue(
    val id: Long,
    val number: Int,
    val title: String,
    val state: IssueState,
    val body: String? = null,
    val author: IssueUser? = null,
    val labels: List<IssueLabel> = emptyList(),
    val assignees: List<IssueUser> = emptyList(),
    val milestone: IssueMilestone? = null,
    val reactions: IssueReactions = IssueReactions(),
    val commentCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val htmlUrl: String? = null,
    val isPullRequest: Boolean = false,
    /** 仓库级 viewer 权限（T14 写操作可见性；null = 未获取，保守隐藏写操作） */
    val viewerPermission: IssueViewerPermission? = null,
    /** Issue GraphQL node id（T14 任务列表 mutation 用；REST 通道为 null） */
    val graphqlId: String? = null,
)

/** 作者/Assignees 用户（复用 REST UserDto 字段） */
@Immutable
data class IssueUser(
    val login: String,
    val avatarUrl: String? = null,
)

/** 标签（LabelChip 展示） */
@Immutable
data class IssueLabel(
    val name: String,
    val color: String? = null,
)

/** 里程碑 */
@Immutable
data class IssueMilestone(
    val title: String,
    val state: IssueState? = null,
)

/** 反应计数（ReactionBar 展示） */
@Immutable
data class IssueReactions(
    val totalCount: Int = 0,
    /** 各反应类型计数（content → count，content 取值 +1/-1/laugh/hooray/confused/heart/rocket/eyes） */
    val counts: Map<String, Int> = emptyMap(),
)

/** 单个反应（add reaction 响应，id 供删除用） */
@Immutable
data class IssueReaction(
    val id: Long,
    val content: String,
    val user: IssueUser? = null,
)

/** 评论（create/update comment 响应，乐观插入后替换临时项） */
@Immutable
data class IssueComment(
    val id: Long,
    val body: String? = null,
    val author: IssueUser? = null,
    val htmlUrl: String? = null,
    val createdAt: String? = null,
    val reactions: IssueReactions = IssueReactions(),
)

/**
 * Issue 写操作上下文（T14，GraphQL IssueWriteContextQuery）。
 *
 * - [viewerLogin]：当前登录用户 login（作者/评论者身份判定）
 * - [viewerPermission]：仓库级权限（决定操作可见性）
 * - [issueNodeId]：Issue GraphQL node id（UpdateIssue mutation 必需；REST 通道不可得）
 */
@Immutable
data class IssueWriteContext(
    val viewerLogin: String? = null,
    val viewerPermission: IssueViewerPermission = IssueViewerPermission.NONE,
    val issueNodeId: String? = null,
)

/**
 * 仓库级 viewer 权限（GitHub GraphQL RepositoryPermission 映射）。
 *
 * 权限决定操作可见性（plan.md §6.1「权限决定可见性」）：
 * - TRIAGE+：Labels/Assignees/Milestone 编辑
 * - WRITE+：关闭/重开他人 Issue、编辑他人 Issue
 * - 作者本人：编辑/关闭/重开自己的 Issue（不受仓库权限限制）
 */
enum class IssueViewerPermission(
    val level: Int,
) {
    NONE(0),
    READ(1),
    TRIAGE(2),
    WRITE(3),
    MAINTAIN(4),
    ADMIN(5),
    ;

    companion object {
        fun fromRaw(raw: String?): IssueViewerPermission =
            when (raw) {
                "ADMIN" -> ADMIN
                "MAINTAIN" -> MAINTAIN
                "WRITE" -> WRITE
                "TRIAGE" -> TRIAGE
                "READ" -> READ
                else -> NONE
            }
    }
}

/** 时间线条目（评论 vs 事件） */
sealed interface IssueTimelineItem {
    /** 唯一 id（LazyColumn key 复用） */
    val id: Long

    /** 评论（body 经 MarkdownViewer 原生渲染） */
    @Immutable
    data class Comment(
        override val id: Long,
        val author: IssueUser? = null,
        val body: String? = null,
        val htmlUrl: String? = null,
        val createdAt: String? = null,
        val reactions: IssueReactions = IssueReactions(),
    ) : IssueTimelineItem

    /** 事件（closed/labeled/cross-referenced/connected 等） */
    @Immutable
    data class Event(
        override val id: Long,
        val type: IssueTimelineEventType,
        val actor: IssueUser? = null,
        val createdAt: String? = null,
        val label: IssueLabel? = null,
        val milestone: IssueMilestone? = null,
        val commitId: String? = null,
        /** cross-referenced 目标 issue */
        val sourceIssue: Issue? = null,
        /** connected/linked 关联 PR */
        val linkedPullRequest: Issue? = null,
    ) : IssueTimelineItem
}

/**
 * 时间线事件类型（GitHub REST `event` 字段映射）。
 *
 * 已知类型枚举化，避免 UI/数据层用 String 字面量做 when 级联（单一事实来源）。
 * 未知类型统一落到 [UNKNOWN]，UI 用兜底文案。
 */
enum class IssueTimelineEventType {
    CLOSED,
    REOPENED,
    LABELED,
    UNLABELED,
    ASSIGNED,
    LOCKED,
    CROSS_REFERENCED,
    CONNECTED,
    LINKED,
    COMMENTED,
    UNKNOWN,
    ;

    companion object {
        fun fromRaw(raw: String): IssueTimelineEventType =
            when (raw) {
                "closed" -> CLOSED
                "reopened" -> REOPENED
                "labeled" -> LABELED
                "unlabeled" -> UNLABELED
                "assigned" -> ASSIGNED
                "locked" -> LOCKED
                "cross-referenced" -> CROSS_REFERENCED
                "connected" -> CONNECTED
                "linked" -> LINKED
                "commented" -> COMMENTED
                else -> UNKNOWN
            }
    }
}

/** 列表过滤（Open/Closed 切换） */
enum class IssueFilter {
    OPEN,
    CLOSED,

    ;

    fun toRaw(): String = if (this == OPEN) "open" else "closed"
}

/** 列表/详情加载错误类型（UI 层映射本地化文案，ViewModel 不产英文） */
enum class IssueErrorType {
    /** 404：Issue 或仓库不存在 */
    NOT_FOUND,

    /** 网络/IO 错误 */
    NETWORK,

    /** 其他未知错误 */
    UNKNOWN,
}
