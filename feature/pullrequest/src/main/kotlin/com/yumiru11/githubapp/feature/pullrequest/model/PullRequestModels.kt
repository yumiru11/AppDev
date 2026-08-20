package com.yumiru11.githubapp.feature.pullrequest.model

/** PR 状态（GitHub REST state + draft/merged_at 字段映射） */
enum class PullRequestState {
    OPEN,
    CLOSED,
    MERGED,
    DRAFT,

    ;

    companion object {
        fun fromRaw(
            state: String,
            draft: Boolean,
            mergedAt: String?,
        ): PullRequestState =
            when {
                draft -> DRAFT
                mergedAt != null -> MERGED
                state == "open" -> OPEN
                else -> CLOSED
            }
    }
}

/** 列表过滤（Open/Closed/All 切换） */
enum class PullRequestFilter {
    OPEN,
    CLOSED,
    ALL,

    ;

    fun toRaw(): String =
        when (this) {
            OPEN -> "open"
            CLOSED -> "closed"
            ALL -> "all"
        }
}

/** 可合并性状态（mergeable + mergeable_state 映射） */
enum class MergeableState {
    /** 可合并（mergeable=true） */
    MERGEABLE,

    /** 冲突（mergeable=false） */
    CONFLICTING,

    /** 待检查（mergeable=null，GitHub 尚未完成合并性检查） */
    UNKNOWN,

    ;

    companion object {
        fun fromRaw(
            mergeable: Boolean?,
            mergeableState: String?,
        ): MergeableState =
            when {
                mergeable == true -> MERGEABLE
                mergeable == false -> CONFLICTING
                mergeableState == "dirty" -> CONFLICTING
                else -> UNKNOWN
            }
    }
}

/** Check Run 状态（status 字段映射） */
enum class CheckRunStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    UNKNOWN,

    ;

    companion object {
        fun fromRaw(raw: String?): CheckRunStatus =
            when (raw) {
                "queued" -> QUEUED
                "in_progress" -> IN_PROGRESS
                "completed" -> COMPLETED
                else -> UNKNOWN
            }
    }
}

/** Check Run 结论（conclusion 字段映射；未完成时为 null → UNKNOWN） */
enum class CheckRunConclusion {
    SUCCESS,
    FAILURE,
    NEUTRAL,
    CANCELLED,
    SKIPPED,
    TIMED_OUT,
    ACTION_REQUIRED,
    UNKNOWN,

    ;

    companion object {
        fun fromRaw(raw: String?): CheckRunConclusion =
            when (raw) {
                "success" -> SUCCESS
                "failure" -> FAILURE
                "neutral" -> NEUTRAL
                "cancelled" -> CANCELLED
                "skipped" -> SKIPPED
                "timed_out" -> TIMED_OUT
                "action_required" -> ACTION_REQUIRED
                else -> UNKNOWN
            }
    }
}

/** Review 状态（reviewed 事件 state 字段映射） */
enum class PullRequestReviewState {
    APPROVED,
    CHANGES_REQUESTED,
    COMMENTED,
    DISMISSED,
    UNKNOWN,

    ;

    companion object {
        fun fromRaw(raw: String?): PullRequestReviewState =
            when (raw) {
                "APPROVED" -> APPROVED
                "CHANGES_REQUESTED" -> CHANGES_REQUESTED
                "COMMENTED" -> COMMENTED
                "DISMISSED" -> DISMISSED
                else -> UNKNOWN
            }
    }
}

/** 文件变更状态（status 字段映射） */
enum class PullRequestFileStatus {
    ADDED,
    MODIFIED,
    REMOVED,
    RENAMED,
    COPIED,
    CHANGED,
    UNKNOWN,

    ;

    companion object {
        fun fromRaw(raw: String?): PullRequestFileStatus =
            when (raw) {
                "added" -> ADDED
                "modified" -> MODIFIED
                "removed" -> REMOVED
                "renamed" -> RENAMED
                "copied" -> COPIED
                "changed" -> CHANGED
                else -> UNKNOWN
            }
    }
}

/** 详情页四 Tab（与网页端对齐） */
enum class PullRequestTab {
    CONVERSATION,
    COMMITS,
    CHECKS,
    FILES,
}

/** 列表/详情加载错误类型（UI 层映射本地化文案，ViewModel 不产英文） */
enum class PullRequestErrorType {
    /** 404：PR 或仓库不存在 */
    NOT_FOUND,

    /** 网络/IO 错误 */
    NETWORK,

    /** 其他未知错误 */
    UNKNOWN,
}

/** PR 列表/详情条目 */
data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: PullRequestState,
    val body: String? = null,
    val author: PullRequestUser? = null,
    val labels: List<PullRequestLabel> = emptyList(),
    val assignees: List<PullRequestUser> = emptyList(),
    val milestone: PullRequestMilestone? = null,
    val commentCount: Int = 0,
    val reviewCommentCount: Int = 0,
    val commitCount: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFiles: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val mergedAt: String? = null,
    val htmlUrl: String? = null,
    val mergeable: Boolean? = null,
    val mergeableState: MergeableState = MergeableState.UNKNOWN,
    val head: PullRequestBranch? = null,
    val base: PullRequestBranch? = null,
    val requestedReviewers: List<PullRequestUser> = emptyList(),
)

/** 作者/Reviewers 用户 */
data class PullRequestUser(
    val login: String,
    val avatarUrl: String? = null,
)

/** 标签（LabelChip 展示） */
data class PullRequestLabel(
    val name: String,
    val color: String? = null,
)

/** 里程碑 */
data class PullRequestMilestone(
    val title: String,
)

/** 分支信息（base ← head） */
data class PullRequestBranch(
    val label: String? = null,
    val ref: String? = null,
    val sha: String? = null,
)

/** 提交条目（Commits Tab） */
data class PullRequestCommit(
    val sha: String,
    val message: String? = null,
    val author: PullRequestUser? = null,
    val createdAt: String? = null,
    val htmlUrl: String? = null,
    val files: List<PullRequestCommitFile> = emptyList(),
)

/** 提交内文件变更摘要（展开 diff 用） */
data class PullRequestCommitFile(
    val filename: String? = null,
    val status: PullRequestFileStatus = PullRequestFileStatus.UNKNOWN,
    val additions: Int = 0,
    val deletions: Int = 0,
)

/** 文件变更条目（Files changed Tab） */
data class PullRequestFile(
    val filename: String,
    val status: PullRequestFileStatus = PullRequestFileStatus.UNKNOWN,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null,
)

/** Check Run 条目（Checks Tab） */
data class CheckRun(
    val id: Long,
    val name: String? = null,
    val status: CheckRunStatus = CheckRunStatus.UNKNOWN,
    val conclusion: CheckRunConclusion = CheckRunConclusion.UNKNOWN,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val outputTitle: String? = null,
    val outputSummary: String? = null,
    val outputText: String? = null,
    val appName: String? = null,
    val htmlUrl: String? = null,
)

/** 合并状态摘要（Checks 摘要行） */
data class CombinedStatus(
    val state: String? = null,
    val totalCount: Int = 0,
)

/** 时间线条目（评论 / Review / 行内评论 / 提交引用 / 事件） */
sealed interface PullRequestTimelineItem {
    /** 唯一 id（LazyColumn key 复用；GitHub 部分事件 id=null 时用负数合成） */
    val id: Long

    /** 普通评论（body 经 MarkdownViewer 原生渲染） */
    data class Comment(
        override val id: Long,
        val author: PullRequestUser? = null,
        val body: String? = null,
        val createdAt: String? = null,
    ) : PullRequestTimelineItem

    /** Review 卡片（approve/comment/request-changes） */
    data class Review(
        override val id: Long,
        val author: PullRequestUser? = null,
        val body: String? = null,
        val state: PullRequestReviewState = PullRequestReviewState.UNKNOWN,
        val submittedAt: String? = null,
    ) : PullRequestTimelineItem

    /** 行内评论（path:line 定位） */
    data class ReviewComment(
        override val id: Long,
        val author: PullRequestUser? = null,
        val body: String? = null,
        val path: String? = null,
        val line: Int? = null,
        val createdAt: String? = null,
    ) : PullRequestTimelineItem

    /** 提交引用（committed 事件） */
    data class CommitReference(
        override val id: Long,
        val author: PullRequestUser? = null,
        val sha: String? = null,
        val message: String? = null,
        val createdAt: String? = null,
    ) : PullRequestTimelineItem

    /** 事件（closed/merged/labeled/cross-referenced 等） */
    data class Event(
        override val id: Long,
        val type: PullRequestTimelineEventType,
        val actor: PullRequestUser? = null,
        val createdAt: String? = null,
        val label: PullRequestLabel? = null,
        /** cross-referenced 目标 issue/PR */
        val sourceIssue: PullRequest? = null,
        /** connected/linked 关联 PR */
        val linkedPullRequest: PullRequest? = null,
        /** head_ref_force_pushed/head_ref_deleted 分支引用 */
        val ref: String? = null,
    ) : PullRequestTimelineItem
}

/**
 * PR 时间线事件类型（GitHub REST `event` 字段映射）。
 *
 * 已知类型枚举化，避免 UI/数据层用 String 字面量做 when 级联（单一事实来源）。
 * 未知类型统一落到 [UNKNOWN]，UI 用兜底文案。
 */
enum class PullRequestTimelineEventType {
    CLOSED,
    REOPENED,
    MERGED,
    LABELED,
    UNLABELED,
    ASSIGNED,
    LOCKED,
    CROSS_REFERENCED,
    CONNECTED,
    LINKED,
    REVIEW_REQUESTED,
    REVIEW_REQUEST_REMOVED,
    HEAD_REF_FORCE_PUSHED,
    HEAD_REF_DELETED,
    BASE_REF_CHANGED,
    READY_FOR_REVIEW,
    CONVERTED_TO_DRAFT,
    COMMENTED,
    REVIEWED,
    COMMITTED,
    UNKNOWN,
    ;

    companion object {
        /**
         * 事件类型映射分支天然多（20+ 已知类型），枚举化收益大于拆分；精准抑制（T3 先例）。
         */
        @Suppress("CyclomaticComplexMethod")
        fun fromRaw(raw: String): PullRequestTimelineEventType =
            when (raw) {
                "closed" -> CLOSED
                "reopened" -> REOPENED
                "merged" -> MERGED
                "labeled" -> LABELED
                "unlabeled" -> UNLABELED
                "assigned" -> ASSIGNED
                "locked" -> LOCKED
                "cross-referenced" -> CROSS_REFERENCED
                "connected" -> CONNECTED
                "linked" -> LINKED
                "review_requested" -> REVIEW_REQUESTED
                "review_request_removed" -> REVIEW_REQUEST_REMOVED
                "head_ref_force_pushed" -> HEAD_REF_FORCE_PUSHED
                "head_ref_deleted" -> HEAD_REF_DELETED
                "base_ref_changed" -> BASE_REF_CHANGED
                "ready_for_review" -> READY_FOR_REVIEW
                "converted_to_draft" -> CONVERTED_TO_DRAFT
                "commented" -> COMMENTED
                "reviewed" -> REVIEWED
                "committed" -> COMMITTED
                else -> UNKNOWN
            }
    }
}
