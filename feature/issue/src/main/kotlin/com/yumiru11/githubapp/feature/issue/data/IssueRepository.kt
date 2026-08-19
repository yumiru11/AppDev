package com.yumiru11.githubapp.feature.issue.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.IssueEventDto
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueMilestone
import com.yumiru11.githubapp.feature.issue.model.IssueReactions
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Issue 数据仓库（T13）：列表分页流 + 详情 + 时间线。
 *
 * - 列表：[issues] 分页流（[IssuePagingSource]），下拉刷新由 UI 层 LazyPagingItems.refresh() 触发
 * - 详情：[getIssue]（GET /repos/{owner}/{repo}/issues/{number}）
 * - 时间线：[timeline]（GET .../timeline，评论/事件/交叉引用/关联 PR 合一）
 */
@Singleton
class IssueRepository
    @Inject
    constructor(
        private val issueApi: IssueApi,
    ) {
        /** Issue 分页流（按 [filter] 过滤 open/closed） */
        fun issues(
            owner: String,
            repo: String,
            filter: IssueFilter,
        ): Flow<PagingData<Issue>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                pagingSourceFactory = { IssuePagingSource(issueApi, owner, repo, filter) },
            ).flow

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

        private companion object {
            const val PAGE_SIZE = 30
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

private fun com.yumiru11.githubapp.core.githubrest.model.LabelDto.toDomain(): IssueLabel = IssueLabel(name = name, color = color)

private fun com.yumiru11.githubapp.core.githubrest.model.MilestoneDto.toDomain(): IssueMilestone =
    IssueMilestone(title = title, state = state?.let { IssueState.fromRaw(it) })

private fun com.yumiru11.githubapp.core.githubrest.model.ReactionsDto.toDomain(): IssueReactions = IssueReactions(totalCount = totalCount)
