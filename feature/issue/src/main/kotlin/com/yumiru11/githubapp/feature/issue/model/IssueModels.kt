package com.yumiru11.githubapp.feature.issue.model

/** Issue 状态（GitHub REST state 字段映射） */
enum class IssueState {
    OPEN,
    CLOSED,

    ;

    companion object {
        fun fromRaw(value: String): IssueState = if (value == "open") OPEN else CLOSED
    }
}

/** Issue 列表/详情条目 */
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
)

/** 作者/Assignees 用户（复用 REST UserDto 字段） */
data class IssueUser(
    val login: String,
    val avatarUrl: String? = null,
)

/** 标签（LabelChip 展示） */
data class IssueLabel(
    val name: String,
    val color: String? = null,
)

/** 里程碑 */
data class IssueMilestone(
    val title: String,
    val state: IssueState? = null,
)

/** 反应计数（ReactionBar 展示） */
data class IssueReactions(
    val totalCount: Int = 0,
)

/** 时间线条目（评论 vs 事件） */
sealed interface IssueTimelineItem {
    /** 唯一 id（LazyColumn key 复用） */
    val id: Long

    /** 评论（body 经 MarkdownViewer 原生渲染） */
    data class Comment(
        override val id: Long,
        val author: IssueUser? = null,
        val body: String? = null,
        val htmlUrl: String? = null,
        val createdAt: String? = null,
        val reactions: IssueReactions = IssueReactions(),
    ) : IssueTimelineItem

    /** 事件（closed/labeled/cross-referenced/connected 等） */
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
