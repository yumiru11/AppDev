package com.yumiru11.githubapp.feature.pullrequest.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.core.githubrest.model.CheckRunDto
import com.yumiru11.githubapp.core.githubrest.model.CombinedStatusDto
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestCommitDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestFileDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestBranch
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommitFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFileStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestLabel
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestMilestone
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReviewState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineEventType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull Request 数据仓库（T15）：列表分页流 + 详情 + 时间线 + 提交 + 文件 + Checks。
 *
 * - 列表：[pulls] 分页流（[PullRequestPagingSource]），下拉刷新由 UI 层 LazyPagingItems.refresh() 触发
 * - 详情：[getPullRequest]（GET /repos/{owner}/{repo}/pulls/{number}）
 * - 时间线：[timeline]（GET .../issues/{number}/timeline，评论/Review/行内评论/提交引用/事件合一）
 * - 提交：[commits]（GET .../pulls/{number}/commits）
 * - 文件：[files]（GET .../pulls/{number}/files）
 * - Checks：[checkRuns]（GET .../commits/{ref}/check-runs）+ [combinedStatus]（GET .../commits/{ref}/status）
 */
@Singleton
class PullRequestRepository
    @Inject
    constructor(
        private val pullRequestApi: PullRequestApi,
    ) {
        /** PR 分页流（按 [filter] 过滤 open/closed/all） */
        fun pulls(
            owner: String,
            repo: String,
            filter: PullRequestFilter,
        ): Flow<PagingData<PullRequest>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                pagingSourceFactory = { PullRequestPagingSource(pullRequestApi, owner, repo, filter) },
            ).flow

        /** 单个 PR 详情 */
        suspend fun getPullRequest(
            owner: String,
            repo: String,
            number: Int,
        ): PullRequest = pullRequestApi.getPullRequest(owner, repo, number).toDomain()

        /** PR 时间线（评论/Review/行内评论/提交引用/事件） */
        suspend fun timeline(
            owner: String,
            repo: String,
            number: Int,
        ): List<PullRequestTimelineItem> =
            pullRequestApi.listTimeline(owner, repo, number).mapIndexed { index, dto -> dto.toTimelineItem(index) }

        /** PR 提交列表 */
        suspend fun commits(
            owner: String,
            repo: String,
            number: Int,
        ): List<PullRequestCommit> = pullRequestApi.listCommits(owner, repo, number).map { it.toDomain() }

        /** PR 文件变更列表 */
        suspend fun files(
            owner: String,
            repo: String,
            number: Int,
        ): List<PullRequestFile> = pullRequestApi.listFiles(owner, repo, number).map { it.toDomain() }

        /** Check Run 列表（按 head sha） */
        suspend fun checkRuns(
            owner: String,
            repo: String,
            headSha: String,
        ): List<CheckRun> = pullRequestApi.listCheckRuns(owner, repo, headSha).checkRuns.map { it.toDomain() }

        /** 合并状态摘要（按 head sha） */
        suspend fun combinedStatus(
            owner: String,
            repo: String,
            headSha: String,
        ): CombinedStatus = pullRequestApi.getCombinedStatus(owner, repo, headSha).toDomain()

        private companion object {
            const val PAGE_SIZE = 30
        }
    }

/** PullRequestDto → [PullRequest] */
internal fun PullRequestDto.toDomain(): PullRequest =
    PullRequest(
        id = id,
        number = number,
        title = title,
        state = PullRequestState.fromRaw(state, draft, mergedAt),
        body = body,
        author = user?.toDomain(),
        labels = labels.map { it.toDomain() },
        assignees = assignees.map { it.toDomain() },
        milestone = milestone?.let { PullRequestMilestone(title = it.title) },
        commentCount = comments,
        reviewCommentCount = reviewComments,
        commitCount = commits,
        additions = additions,
        deletions = deletions,
        changedFiles = changedFiles,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mergedAt = mergedAt,
        htmlUrl = htmlUrl,
        mergeable = mergeable,
        mergeableState = MergeableState.fromRaw(mergeable, mergeableState),
        head = head?.toDomain(),
        base = base?.toDomain(),
        requestedReviewers = requestedReviewers.map { it.toDomain() },
    )

/**
 * IssueEventDto → [PullRequestTimelineItem]。
 *
 * 判别规则（PR 时间线）：
 * - reviewed → [Review]（state 字段为 Review 状态）
 * - commented 含 path → [ReviewComment]（行内评论）；不含 path → [Comment]
 * - committed → [CommitReference]（sha/message）
 * - cross-referenced → [Event.sourceIssue]；connected/linked → [linkedPullRequest]
 * - 其余 → [Event]
 *
 * @param ordinal 时间线中的下标；GitHub 对部分事件返回 id=null，
 *   此时用负数合成稳定且唯一的 key（真实 id 均为正数）。
 */
internal fun IssueEventDto.toTimelineItem(ordinal: Int): PullRequestTimelineItem {
    val type = PullRequestTimelineEventType.fromRaw(event)
    val id = this.id ?: -(ordinal + 1).toLong()
    return when {
        type == PullRequestTimelineEventType.REVIEWED -> {
            PullRequestTimelineItem.Review(
                id = id,
                author = actor?.toDomain(),
                body = body,
                state = PullRequestReviewState.fromRaw(state),
                submittedAt = createdAt,
            )
        }

        type == PullRequestTimelineEventType.COMMENTED && !path.isNullOrBlank() -> {
            PullRequestTimelineItem.ReviewComment(
                id = id,
                author = actor?.toDomain(),
                body = body,
                path = path,
                line = line,
                createdAt = createdAt,
            )
        }

        type == PullRequestTimelineEventType.COMMENTED -> {
            PullRequestTimelineItem.Comment(
                id = id,
                author = actor?.toDomain(),
                body = body,
                createdAt = createdAt,
            )
        }

        type == PullRequestTimelineEventType.COMMITTED -> {
            PullRequestTimelineItem.CommitReference(
                id = id,
                author = actor?.toDomain(),
                sha = sha,
                message = message,
                createdAt = createdAt,
            )
        }

        else -> {
            PullRequestTimelineItem.Event(
                id = id,
                type = type,
                actor = actor?.toDomain(),
                createdAt = createdAt,
                label = label?.let { PullRequestLabel(name = it.name, color = it.color) },
                sourceIssue = if (type == PullRequestTimelineEventType.CROSS_REFERENCED) source?.issue?.toDomain() else null,
                linkedPullRequest =
                    if (type == PullRequestTimelineEventType.CONNECTED || type == PullRequestTimelineEventType.LINKED) {
                        source?.issue?.toDomain()
                    } else {
                        null
                    },
                ref = ref,
            )
        }
    }
}

private fun PullRequestCommitDto.toDomain(): PullRequestCommit =
    PullRequestCommit(
        sha = sha,
        message = commit?.message,
        author = author?.toDomain(),
        createdAt = commit?.author?.date,
        htmlUrl = htmlUrl,
        files = files.map { it.toDomain() },
    )

private fun com.yumiru11.githubapp.core.githubrest.model.PullRequestCommitFileDto.toDomain(): PullRequestCommitFile =
    PullRequestCommitFile(
        filename = filename,
        status = PullRequestFileStatus.fromRaw(status),
        additions = additions,
        deletions = deletions,
    )

private fun PullRequestFileDto.toDomain(): PullRequestFile =
    PullRequestFile(
        filename = filename,
        status = PullRequestFileStatus.fromRaw(status),
        additions = additions,
        deletions = deletions,
        changes = changes,
        patch = patch,
    )

internal fun CheckRunDto.toDomain(): CheckRun =
    CheckRun(
        id = id,
        name = name,
        status = CheckRunStatus.fromRaw(status),
        conclusion = CheckRunConclusion.fromRaw(conclusion),
        startedAt = startedAt,
        completedAt = completedAt,
        outputTitle = output?.title,
        outputSummary = output?.summary,
        outputText = output?.text,
        appName = app?.name,
        htmlUrl = htmlUrl,
    )

private fun CombinedStatusDto.toDomain(): CombinedStatus =
    CombinedStatus(
        state = state,
        totalCount = totalCount,
    )

private fun com.yumiru11.githubapp.core.githubrest.model.PullRequestBranchDto.toDomain(): PullRequestBranch =
    PullRequestBranch(
        label = label,
        ref = ref,
        sha = sha,
    )

private fun UserDto.toDomain(): PullRequestUser = PullRequestUser(login = login, avatarUrl = avatarUrl)

/** IssueDto → [PullRequest]（cross-referenced/connected/linked 事件引用的 issue/PR 摘要） */
private fun IssueDto.toDomain(): PullRequest =
    PullRequest(
        id = id,
        number = number,
        title = title,
        state = PullRequestState.fromRaw(state, draft = false, mergedAt = null),
        author = user?.toDomain(),
        labels = labels.map { it.toDomain() },
        assignees = assignees.map { it.toDomain() },
        milestone = milestone?.let { PullRequestMilestone(title = it.title) },
        commentCount = comments,
        createdAt = createdAt,
        htmlUrl = htmlUrl,
    )

private fun com.yumiru11.githubapp.core.githubrest.model.LabelDto.toDomain(): PullRequestLabel =
    PullRequestLabel(name = name, color = color)
