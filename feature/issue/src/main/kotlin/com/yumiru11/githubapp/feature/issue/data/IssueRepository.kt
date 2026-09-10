@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：GraphQL 通道异常统一兜底（PAT 模式不支持 GraphQL / 网络瞬断）
// - SwallowedException：getIssueWriteContext 失败返回保守空上下文、toggleTaskListItem 失败降级 REST，
//   均为有意的降级路径（异常链在 REST 兜底失败时经 HttpException 保留）

package com.yumiru11.githubapp.feature.issue.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.database.dao.IssueDao
import com.yumiru11.githubapp.core.githubgraphql.generated.IssueWriteContextQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.UpdateIssueMutation
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.model.CreateCommentRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateIssueRequest
import com.yumiru11.githubapp.core.githubrest.model.CreateReactionRequest
import com.yumiru11.githubapp.core.githubrest.model.IssueCommentDto
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.core.githubrest.model.ReactionDto
import com.yumiru11.githubapp.core.githubrest.model.ReactionsDto
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionRequest
import com.yumiru11.githubapp.core.githubrest.model.UpdateIssueRequest
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueComment
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueMilestone
import com.yumiru11.githubapp.feature.issue.model.IssueReaction
import com.yumiru11.githubapp.feature.issue.model.IssueReactions
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Issue 数据仓库（T13 读 + T14 写）。
 *
 * 读：列表分页流 + 详情 + 时间线（REST）。
 * 写（T14）：创建/编辑/关闭重开/评论增改删/反应增删走 REST（写优先通道）；
 * 任务列表 checkbox 反向同步走 GraphQL UpdateIssue mutation（node id 来自
 * [getIssueWriteContext]），GraphQL 失败（PAT 模式/网络）降级 REST PATCH body。
 * 全部写操作由 ViewModel 层做乐观更新 + 失败回滚。
 */
@Singleton
@Suppress("TooManyFunctions") // #163 新增订阅/元数据读写后 21 个职责相关方法（读/写/订阅/GraphQL 通道），拆类反损内聚（PullRequestRepository 同款先例）
class IssueRepository
    @Inject
    constructor(
        private val issueApi: IssueApi,
        private val apolloClient: ApolloClient,
        private val issueDao: IssueDao,
    ) {
        /**
         * Issue 分页流（按 [filter] 过滤 open/closed）。
         *
         * 远端由 [IssueRemoteMediator] 写 Room，本地数据源 = Room 分页查询（plan.md §4.6）。
         * 好处：断网/离线可读上次访问列表；下拉刷新（LazyPagingItems.refresh()）触发
         * RemoteMediator REFRESH → 网络结果与本地缓存合并。
         */
        @OptIn(ExperimentalPagingApi::class)
        fun issues(
            owner: String,
            repo: String,
            filter: IssueFilter,
        ): Flow<PagingData<Issue>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                remoteMediator = IssueRemoteMediator(issueApi, issueDao, owner, repo, filter),
                pagingSourceFactory = { issueDao.pagingSource(owner, repo, filter.toRaw()) },
            ).flow.map { data -> data.map { entity -> entity.toDomain() } }

        /** 单个 Issue 详情 */
        suspend fun getIssue(
            owner: String,
            repo: String,
            number: Int,
        ): Issue = issueApi.getIssue(owner, repo, number).toDomain()

        /** Issue 时间线（评论 vs 事件，含交叉引用/关联 PR） */
        suspend fun timeline(
            owner: String,
            repo: String,
            number: Int,
        ): List<IssueTimelineItem> = issueApi.listTimeline(owner, repo, number).mapIndexed { index, dto -> dto.toTimelineItem(index) }

        // ---- T14 写操作（REST 写优先通道） ----

        /** 创建 Issue（标题/正文/标签） */
        suspend fun createIssue(
            owner: String,
            repo: String,
            title: String,
            body: String?,
            labels: List<String>?,
        ): Issue = issueApi.createIssue(owner, repo, CreateIssueRequest(title = title, body = body, labels = labels)).toDomain()

        /** 编辑 Issue title/body/state（关闭/重开走 state） */
        suspend fun updateIssue(
            owner: String,
            repo: String,
            number: Int,
            title: String? = null,
            body: String? = null,
            state: String? = null,
        ): Issue = issueApi.updateIssue(owner, repo, number, UpdateIssueRequest(title = title, body = body, state = state)).toDomain()

        /**
         * 编辑 Issue 元数据（Labels/Assignees/Milestone，权限决定可见性；#163 L02）。
         *
         * **只把变更字段交给 PATCH**：未变更字段保持 null → [UpdateIssueRequest] 序列化器不携带；
         * 清空语义——labels/assignees 传空列表（GitHub 支持 `[]` 清空），milestone 传
         * [clearMilestone] = true（GitHub 需显式 `"milestone": null`）。
         */
        suspend fun updateIssueMeta(
            owner: String,
            repo: String,
            number: Int,
            labels: List<String>? = null,
            assignees: List<String>? = null,
            milestone: Long? = null,
            clearMilestone: Boolean = false,
        ): Issue =
            issueApi
                .updateIssue(
                    owner,
                    repo,
                    number,
                    UpdateIssueRequest(
                        labels = labels,
                        assignees = assignees,
                        milestone = milestone,
                        clearMilestone = clearMilestone,
                    ),
                ).toDomain()

        // ---- #163 L01：Issue 级订阅（Subscribe / Unsubscribe） ----

        /**
         * Issue 订阅态：200 → `subscribed`；**404 = 未订阅**（GitHub 语义）→ false。
         *
         * 401/403 等其余 [HttpException] 正常抛出，由 ViewModel 经既有 GitHubError 归一
         * 兜底为「未订阅 + 不阻塞详情页」（[retrofit2.HttpException] 之外的异常同样抛出）。
         */
        suspend fun isSubscribed(
            owner: String,
            repo: String,
            number: Int,
        ): Boolean =
            try {
                issueApi.getIssueSubscription(owner, repo, number).subscribed
            } catch (e: HttpException) {
                if (e.code() == HTTP_NOT_FOUND) false else throw e
            }

        /** 订阅 Issue（PUT .../subscription，body `{"subscribed": true}`） */
        suspend fun subscribe(
            owner: String,
            repo: String,
            number: Int,
        ) {
            issueApi.subscribeIssue(owner, repo, number, SubscriptionRequest(subscribed = true))
        }

        /** 取消订阅 Issue（DELETE .../subscription，204） */
        suspend fun unsubscribe(
            owner: String,
            repo: String,
            number: Int,
        ) {
            issueApi.unsubscribeIssue(owner, repo, number)
        }

        // ---- #163 L02：元数据编辑候选项（只读端点） ----

        /** 仓库标签列表（标签选择器候选项） */
        suspend fun getLabels(
            owner: String,
            repo: String,
        ): List<IssueLabel> = issueApi.listLabels(owner, repo).map { it.toDomain() }

        /** 可指派用户列表（Assignee 选择器候选项） */
        suspend fun getAssignees(
            owner: String,
            repo: String,
        ): List<IssueUser> = issueApi.listAssignees(owner, repo).map { it.toDomain() }

        /** 里程碑列表（含已关闭 → state=all；Milestone 单选候选项） */
        suspend fun getMilestones(
            owner: String,
            repo: String,
        ): List<IssueMilestone> = issueApi.listMilestones(owner, repo).map { it.toDomain() }

        /** 新增评论 */
        suspend fun createComment(
            owner: String,
            repo: String,
            number: Int,
            body: String,
        ): IssueComment = issueApi.createComment(owner, repo, number, CreateCommentRequest(body = body)).toDomain()

        /** 编辑评论 */
        suspend fun updateComment(
            owner: String,
            repo: String,
            commentId: Long,
            body: String,
        ): IssueComment = issueApi.updateComment(owner, repo, commentId, CreateCommentRequest(body = body)).toDomain()

        /** 删除评论 */
        suspend fun deleteComment(
            owner: String,
            repo: String,
            commentId: Long,
        ) {
            issueApi.deleteComment(owner, repo, commentId)
        }

        /** 给 Issue 加反应 */
        suspend fun addIssueReaction(
            owner: String,
            repo: String,
            number: Int,
            content: String,
        ): IssueReaction = issueApi.addIssueReaction(owner, repo, number, CreateReactionRequest(content = content)).toDomain()

        /** 删除 Issue 反应（仅本人添加的） */
        suspend fun removeIssueReaction(
            owner: String,
            repo: String,
            number: Int,
            reactionId: Long,
        ) {
            issueApi.removeIssueReaction(owner, repo, number, reactionId)
        }

        /** 给评论加反应 */
        suspend fun addCommentReaction(
            owner: String,
            repo: String,
            commentId: Long,
            content: String,
        ): IssueReaction = issueApi.addCommentReaction(owner, repo, commentId, CreateReactionRequest(content = content)).toDomain()

        /** 删除评论反应（仅本人添加的） */
        suspend fun removeCommentReaction(
            owner: String,
            repo: String,
            commentId: Long,
            reactionId: Long,
        ) {
            issueApi.removeCommentReaction(owner, repo, commentId, reactionId)
        }

        // ---- T14 GraphQL 通道 ----

        /**
         * 获取 Issue 写操作上下文（viewer login + 仓库权限 + Issue node id）。
         *
         * GraphQL 优先；失败（PAT 模式不支持 GraphQL / 网络）返回保守空上下文
         * （权限 NONE → UI 隐藏写操作），不抛异常。
         */
        suspend fun getIssueWriteContext(
            owner: String,
            repo: String,
            number: Int,
        ): IssueWriteContext =
            try {
                val response =
                    apolloClient
                        .query(IssueWriteContextQuery(owner = owner, name = repo, number = number))
                        .fetchPolicy(FetchPolicy.NetworkOnly)
                        .execute()
                val repository = response.data?.repository
                if (repository != null) {
                    IssueWriteContext(
                        viewerLogin = response.data?.viewer?.login,
                        viewerPermission = IssueViewerPermission.fromRaw(repository.viewerPermission?.rawValue),
                        issueNodeId = repository.issue?.id,
                    )
                } else {
                    IssueWriteContext()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                IssueWriteContext()
            }

        /**
         * 任务列表 checkbox 反向同步：翻转正文第 [index] 个任务项为 [checked] 并持久化。
         *
         * GraphQL UpdateIssue mutation 优先（需 [nodeId]）；失败降级 REST PATCH body。
         * 成功后重新拉取详情返回规范 [Issue]（mutation 返回字段子集不全）。
         */
        suspend fun toggleTaskListItem(
            owner: String,
            repo: String,
            number: Int,
            nodeId: String?,
            body: String,
            index: Int,
            checked: Boolean,
        ): Issue {
            val newBody = flipTaskListItem(body, index, checked)
            if (nodeId != null) {
                try {
                    val response =
                        apolloClient
                            .mutation(UpdateIssueMutation(id = nodeId, body = Optional.present(newBody)))
                            .execute()
                    if (response.data?.updateIssue?.issue != null) {
                        return getIssue(owner, repo, number)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // GraphQL 失败（PAT 模式/网络/协议错误）→ REST 兜底
                }
            }
            return updateIssue(owner, repo, number, body = newBody)
        }

        private companion object {
            const val PAGE_SIZE = 30

            /** GitHub 未订阅语义（GET .../subscription 返回 404） */
            const val HTTP_NOT_FOUND = 404
        }
    }

/** IssueDto → [Issue] */
internal fun IssueDto.toDomain(): Issue =
    Issue(
        id = id,
        number = number,
        title = title,
        state = IssueState.fromRaw(state),
        body = body,
        author = user?.toDomain(),
        labels = labels.map { it.toDomain() },
        assignees = assignees.map { it.toDomain() },
        milestone = milestone?.toDomain(),
        reactions = reactions?.toDomain() ?: IssueReactions(),
        commentCount = comments,
        createdAt = createdAt,
        updatedAt = updatedAt,
        htmlUrl = htmlUrl,
        isPullRequest = pullRequest != null,
    )

/** IssueCommentDto → [IssueComment] */
internal fun IssueCommentDto.toDomain(): IssueComment =
    IssueComment(
        id = id,
        body = body,
        author = user?.toDomain(),
        htmlUrl = htmlUrl,
        createdAt = createdAt,
        reactions = reactions?.toDomain() ?: IssueReactions(),
    )

/** ReactionDto → [IssueReaction] */
internal fun ReactionDto.toDomain(): IssueReaction =
    IssueReaction(
        id = id,
        content = content,
        user = user?.toDomain(),
    )

/** [IssueComment] → 时间线评论项（乐观插入后替换临时项） */
internal fun IssueComment.toTimelineItem(): IssueTimelineItem.Comment =
    IssueTimelineItem.Comment(
        id = id,
        author = author,
        body = body,
        htmlUrl = htmlUrl,
        createdAt = createdAt,
        reactions = reactions,
    )

/**
 * IssueEventDto → [IssueTimelineItem]。
 *
 * 评论项（commented）→ [IssueTimelineItem.Comment]；其余 → [IssueTimelineItem.Event]。
 * cross-referenced → [Event.sourceIssue]；connected/linked → [linkedPullRequest]。
 *
 * @param ordinal 时间线中的下标；GitHub 对部分事件（如 cross-referenced）返回 id=null，
 *   此时用负数合成稳定且唯一的 key（真实 id 均为正数）。
 */
internal fun IssueEventDto.toTimelineItem(ordinal: Int): IssueTimelineItem {
    val type = IssueTimelineEventType.fromRaw(event)
    val id = this.id ?: -(ordinal + 1).toLong()
    return if (type == IssueTimelineEventType.COMMENTED) {
        IssueTimelineItem.Comment(
            id = id,
            author = actor?.toDomain(),
            body = body,
            htmlUrl = htmlUrl,
            createdAt = createdAt,
            reactions = reactions?.toDomain() ?: IssueReactions(),
        )
    } else {
        IssueTimelineItem.Event(
            id = id,
            type = type,
            actor = actor?.toDomain(),
            createdAt = createdAt,
            label = label?.toDomain(),
            milestone = milestone?.toDomain(),
            commitId = commitId,
            sourceIssue = if (type == IssueTimelineEventType.CROSS_REFERENCED) source?.issue?.toDomain() else null,
            linkedPullRequest =
                if (type == IssueTimelineEventType.CONNECTED || type == IssueTimelineEventType.LINKED) {
                    source?.issue?.toDomain()
                } else {
                    null
                },
        )
    }
}

private fun com.yumiru11.githubapp.core.githubrest.model.UserDto.toDomain(): IssueUser = IssueUser(login = login, avatarUrl = avatarUrl)

private fun com.yumiru11.githubapp.core.githubrest.model.LabelDto.toDomain(): IssueLabel =
    IssueLabel(name = name, color = color, description = description)

private fun com.yumiru11.githubapp.core.githubrest.model.MilestoneDto.toDomain(): IssueMilestone =
    IssueMilestone(
        title = title,
        state = state?.let { IssueState.fromRaw(it) },
        number = number,
        dueOn = dueOn,
    )

private fun ReactionsDto.toDomain(): IssueReactions =
    IssueReactions(
        totalCount = totalCount,
        counts =
            buildMap {
                if (plusOne > 0) put("+1", plusOne)
                if (minusOne > 0) put("-1", minusOne)
                if (laugh > 0) put("laugh", laugh)
                if (hooray > 0) put("hooray", hooray)
                if (confused > 0) put("confused", confused)
                if (heart > 0) put("heart", heart)
                if (rocket > 0) put("rocket", rocket)
                if (eyes > 0) put("eyes", eyes)
            },
    )

/**
 * 翻转 markdown 正文中第 [index] 个任务列表项的勾选状态为 [checked]。
 *
 * index 语义与 WebView renderer.js 一致：文档中 `- [ ]`/`- [x]`/`* [ ]`/`+ [ ]`/`1. [ ]`
 * 任务列表项的出现顺序（0 起，含嵌套缩进）。未找到对应项时原样返回。
 */
internal fun flipTaskListItem(
    markdown: String,
    index: Int,
    checked: Boolean,
): String {
    val newState = if (checked) "[x]" else "[ ]"
    var taskIndex = 0
    return markdown
        .lines()
        .map { line ->
            val match = TASK_LIST_ITEM_REGEX.matchEntire(line)
            if (match != null) {
                val isTarget = taskIndex == index
                taskIndex++
                if (isTarget) {
                    match.groupValues[1] + newState + match.groupValues[3]
                } else {
                    line
                }
            } else {
                line
            }
        }.joinToString("\n")
}

/** 任务列表项：可选缩进 + 无序/有序列表标记 + `[ ]`/`[x]` 复选框 + 剩余文本 */
private val TASK_LIST_ITEM_REGEX = Regex("""^(\s*(?:[-*+]|\d+\.)\s+)\[([ xX])\](.*)$""")
