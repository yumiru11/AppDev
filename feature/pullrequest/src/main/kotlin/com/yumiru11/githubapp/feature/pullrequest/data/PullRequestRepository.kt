@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：GraphQL 通道异常统一兜底（fine-grained PAT 不支持 / 网络瞬断）
// - SwallowedException：reviewThreadContext 失败返回保守空上下文（T14 getIssueWriteContext 同款降级）

package com.yumiru11.githubapp.feature.pullrequest.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.githubgraphql.generated.PullRequestReviewThreadsQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.ResolveReviewThreadMutation
import com.yumiru11.githubapp.core.githubgraphql.generated.UnresolveReviewThreadMutation
import com.yumiru11.githubapp.core.githubgraphql.generated.type.ResolveReviewThreadInput
import com.yumiru11.githubapp.core.githubgraphql.generated.type.UnresolveReviewThreadInput
import com.yumiru11.githubapp.core.githubrest.api.PullRequestApi
import com.yumiru11.githubapp.core.githubrest.api.RepoManagementApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.model.CheckRunDto
import com.yumiru11.githubapp.core.githubrest.model.CombinedStatusDto
import com.yumiru11.githubapp.core.githubrest.model.CreateReviewCommentRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateReviewRequest
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.core.githubrest.model.MergePullRequestRequest
import com.yumiru11.githubapp.core.githubrest.model.PullRequestCommitDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestFileDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestReviewCommentDto
import com.yumiru11.githubapp.core.githubrest.model.PullRequestReviewDto
import com.yumiru11.githubapp.core.githubrest.model.RepositoryPermissionsDto
import com.yumiru11.githubapp.core.githubrest.model.UpdateBranchRequest
import com.yumiru11.githubapp.core.githubrest.model.UpdateReviewCommentRequest
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentAnchor
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestBranch
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommitFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFileStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestLabel
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestMergeMethod
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestMilestone
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReview
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReviewState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineEventType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestUser
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThreadContext
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
import kotlinx.coroutines.CancellationException
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
        private val repositoryApi: RepositoryApi,
        private val repoManagementApi: RepoManagementApi,
        private val apolloClient: ApolloClient,
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

        /** 行内评论列表（T16；GET /pulls/{number}/comments） */
        suspend fun reviewComments(
            owner: String,
            repo: String,
            number: Int,
        ): List<ReviewComment> = pullRequestApi.listReviewComments(owner, repo, number).map { it.toDomain() }

        /** 新增行内评论（T16；REST 写优先通道） */
        suspend fun createReviewComment(
            owner: String,
            repo: String,
            number: Int,
            anchor: LineCommentAnchor,
            body: String,
            commitId: String,
        ): ReviewComment =
            pullRequestApi
                .createReviewComment(
                    owner,
                    repo,
                    number,
                    CreateReviewCommentRequest(
                        body = body,
                        commitId = commitId,
                        path = anchor.path,
                        line = anchor.line,
                        side = anchor.sideRaw,
                    ),
                ).toDomain()

        /** 回复行内评论（T16；in_reply_to_id 定位） */
        suspend fun replyReviewComment(
            owner: String,
            repo: String,
            number: Int,
            path: String,
            inReplyToId: Long,
            body: String,
        ): ReviewComment =
            pullRequestApi
                .createReviewComment(
                    owner,
                    repo,
                    number,
                    CreateReviewCommentRequest(body = body, path = path, inReplyToId = inReplyToId),
                ).toDomain()

        /** 编辑行内评论（T16 接口就绪；UI v1 未接线，留 T17 Review 使用） */
        suspend fun updateReviewComment(
            owner: String,
            repo: String,
            commentId: Long,
            body: String,
        ): ReviewComment =
            pullRequestApi
                .updateReviewComment(owner, repo, commentId, UpdateReviewCommentRequest(body = body))
                .toDomain()

        /** 删除行内评论（T16 接口就绪；UI v1 未接线，留 T17 Review 使用） */
        suspend fun deleteReviewComment(
            owner: String,
            repo: String,
            commentId: Long,
        ) {
            pullRequestApi.deleteReviewComment(owner, repo, commentId)
        }

        // ── T17：Review / Merge / Update branch / 删除分支 ─────────────────

        /**
         * 仓库写权限与默认分支（T17：MergeBox/Review 显隐 + 默认分支不可删判断）。
         *
         * 选用 REST GET /repos/{owner}/{repo} 而非 GraphQL viewerPermission：
         * REST 对所有令牌形态可用（含 fine-grained PAT 的 REST-only 降级通道），
         * 与 reviewThreadContext 同款「失败保守降级」——网络异常 → UNKNOWN（隐藏写入口），不抛。
         */
        suspend fun repositoryControl(
            owner: String,
            repo: String,
        ): RepositoryControl =
            try {
                val dto = repositoryApi.getRepository(owner, repo)
                RepositoryControl(
                    viewerPermission = dto.permissions.toViewerPermission(),
                    defaultBranch = dto.defaultBranch,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RepositoryControl()
            }

        /** 提交 Review（T17；REST 写优先；返回领域 Review 供乐观项替换） */
        suspend fun submitReview(
            owner: String,
            repo: String,
            number: Int,
            conclusion: ReviewConclusion,
            body: String,
        ): PullRequestReview =
            pullRequestApi
                .createReview(
                    owner,
                    repo,
                    number,
                    CreateReviewRequest(
                        body = body.takeIf { it.isNotBlank() },
                        event = conclusion.toRaw(),
                    ),
                ).toDomain()

        /** 合并 PR（T17；merged=false = 已被合并/无需新提交 → 调用方按成功处理并刷新） */
        suspend fun mergePullRequest(
            owner: String,
            repo: String,
            number: Int,
            method: PullRequestMergeMethod,
            commitTitle: String,
            commitMessage: String,
            headSha: String?,
        ): Boolean =
            pullRequestApi
                .mergePullRequest(
                    owner,
                    repo,
                    number,
                    MergePullRequestRequest(
                        commitTitle = commitTitle.takeIf { it.isNotBlank() },
                        commitMessage = commitMessage.takeIf { it.isNotBlank() },
                        sha = headSha,
                        mergeMethod = method.toRaw(),
                    ),
                ).merged

        /** Update branch（T17；仅同仓库 PR；expected_head_sha 缺省 = 最新 head） */
        suspend fun updateBranch(
            owner: String,
            repo: String,
            number: Int,
            expectedHeadSha: String?,
        ) {
            pullRequestApi.updateBranch(owner, repo, number, UpdateBranchRequest(expectedHeadSha = expectedHeadSha))
        }

        /** 删除分支（T17；git refs 端点；默认分支不可删，GitHub 返回 422） */
        suspend fun deleteBranch(
            owner: String,
            repo: String,
            branch: String,
        ) {
            repoManagementApi.deleteBranch(owner, repo, branch)
        }

        /**
         * 会话上下文（GraphQL reviewThreads 查询，T16）。
         *
         * GraphQL 不可用（fine-grained PAT 不支持 / 网络异常）→ 保守空上下文
         * （pullRequestNodeId=null，UI 隐藏解析入口），不抛异常（T14 getIssueWriteContext 同款降级）。
         */
        suspend fun reviewThreadContext(pullRequestNodeId: String?): ReviewThreadContext {
            if (pullRequestNodeId == null) return ReviewThreadContext()
            return try {
                val response =
                    apolloClient
                        .query(PullRequestReviewThreadsQuery(id = pullRequestNodeId))
                        .fetchPolicy(FetchPolicy.NetworkOnly)
                        .execute()
                val rawThreads =
                    response.data
                        ?.node
                        ?.onPullRequest
                        ?.reviewThreads
                        ?.nodes
                        ?.mapNotNull { node ->
                            node?.let {
                                RawReviewThread(
                                    id = it.id,
                                    path = it.path,
                                    side = it.diffSide.rawValue,
                                    line = it.line,
                                    originalLine = it.originalLine,
                                    isResolved = it.isResolved,
                                    commentIds =
                                        it.comments.nodes
                                            ?.mapNotNull { comment -> comment?.id }
                                            .orEmpty(),
                                )
                            }
                        }.orEmpty()
                ReviewThreadContext(
                    pullRequestNodeId = pullRequestNodeId,
                    threads = rawThreads.toReviewThreads(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReviewThreadContext()
            }
        }

        /** 解决/解除会话（T16；GraphQL 是唯一通道——REST 无解析端点） */
        suspend fun setThreadResolved(
            threadId: String,
            resolved: Boolean,
        ) {
            if (resolved) {
                apolloClient.mutation(ResolveReviewThreadMutation(input = ResolveReviewThreadInput(threadId = threadId))).execute()
            } else {
                apolloClient.mutation(UnresolveReviewThreadMutation(input = UnresolveReviewThreadInput(threadId = threadId))).execute()
            }
        }

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
        nodeId = nodeId,
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

/** 仓库写控制上下文（T17：显隐开关数据源；加载失败 → 保守空值） */
data class RepositoryControl(
    val viewerPermission: ViewerPermission = ViewerPermission.UNKNOWN,
    val defaultBranch: String? = null,
)

/** GraphQL reviewThreads 响应的最小映射源（解包自 Apollo 响应，交给纯函数映射便于单测） */
data class RawReviewThread(
    val id: String,
    val path: String,
    val side: String?,
    val line: Int?,
    val originalLine: Int?,
    val isResolved: Boolean,
    val commentIds: List<String>,
)

internal fun List<RawReviewThread>.toReviewThreads(): List<ReviewThread> = map { it.toDomain() }

private fun RawReviewThread.toDomain(): ReviewThread =
    ReviewThread(
        id = id,
        path = path,
        side = DiffSide.fromRaw(side),
        line = line,
        originalLine = originalLine,
        isResolved = isResolved,
        commentIds = commentIds,
    )

internal fun PullRequestReviewCommentDto.toDomain(): ReviewComment =
    ReviewComment(
        id = id,
        body = body,
        author = user?.toDomain(),
        path = path,
        line = line,
        originalLine = originalLine,
        side = DiffSide.fromRaw(side),
        commitId = commitId,
        createdAt = createdAt,
        inReplyToId = inReplyToId,
        resolved = resolved ?: false,
        nodeId = nodeId,
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
        repoFullName = repo?.fullName,
    )

/** 权限位 → 会话权限（admin/maintain/push → WRITE；仅 pull/triage → READ；缺失 → UNKNOWN） */
internal fun RepositoryPermissionsDto?.toViewerPermission(): ViewerPermission =
    when {
        this == null -> ViewerPermission.UNKNOWN
        admin || maintain || push -> ViewerPermission.WRITE
        else -> ViewerPermission.READ
    }

/** PullRequestReviewDto → [PullRequestReview]（乐观项替换用） */
internal fun PullRequestReviewDto.toDomain(): PullRequestReview =
    PullRequestReview(
        id = id,
        author = user?.toDomain(),
        body = body,
        state = PullRequestReviewState.fromRaw(state),
        submittedAt = submittedAt,
    )

/** [PullRequestReview] → 时间线条目（乐观临时项替换后落回时间线） */
internal fun PullRequestReview.toTimelineItem(): PullRequestTimelineItem.Review =
    PullRequestTimelineItem.Review(
        id = id,
        author = author,
        body = body,
        state = state,
        submittedAt = submittedAt,
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
