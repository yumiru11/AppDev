package com.yumiru11.githubapp.feature.home.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubrest.api.EventsApi
import com.yumiru11.githubapp.core.githubrest.model.EventDto
import com.yumiru11.githubapp.feature.home.model.FeedEventType
import com.yumiru11.githubapp.feature.home.model.FeedItem
import retrofit2.HttpException
import java.io.IOException

/**
 * 首页动态分页数据源：按 [login] 请求 GET /users/{login}/received_events 并映射为领域模型。
 *
 * 未知事件类型客户端过滤（mapNotNull），下一页判定以原始页大小为准
 * （过滤不截断分页，同 NotificationsPagingSource 的 mention 过滤先例）。
 * 错误语义：网络/HTTP 错误一律转 [LoadResult.Error]，UI 层 loadState 驱动错误态与重试。
 */
class FeedPagingSource(
    private val eventsApi: EventsApi,
    private val login: String,
) : PagingSource<Int, FeedItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedItem> =
        try {
            val page = params.key ?: STARTING_PAGE
            val events =
                eventsApi.receivedEvents(
                    login = login,
                    page = page,
                    perPage = params.loadSize,
                )
            LoadResult.Page(
                data = events.mapNotNull(EventDto::toDomain),
                prevKey = if (page > STARTING_PAGE) page - 1 else null,
                nextKey = if (events.isNotEmpty()) page + 1 else null,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Int, FeedItem>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { closestPage ->
                closestPage.prevKey?.plus(1) ?: closestPage.nextKey?.minus(1)
            }
        }

    private companion object {
        const val STARTING_PAGE = 1
    }
}

/** 事件类型常量（GitHub Events API type 字段字面量） */
private const val TYPE_ISSUES = "IssuesEvent"
private const val TYPE_ISSUE_COMMENT = "IssueCommentEvent"
private const val TYPE_PULL_REQUEST = "PullRequestEvent"
private const val TYPE_PUSH = "PushEvent"
private const val TYPE_WATCH = "WatchEvent"
private const val TYPE_FORK = "ForkEvent"

/**
 * EventDto → [FeedItem]；不支持展示的事件类型返回 null（分页源侧过滤）。
 *
 * 点击目标：issue/PR 用载荷内 html_url；push/star/fork 无内容页链接，
 * 构造仓库链接（https://github.com/{owner}/{repo}）→ GitHubLinkParser 解析为 REPO 路由。
 */
private fun EventDto.toDomain(): FeedItem? {
    val eventType =
        when (type) {
            TYPE_ISSUES -> FeedEventType.ISSUE
            TYPE_ISSUE_COMMENT -> FeedEventType.ISSUE_COMMENT
            TYPE_PULL_REQUEST -> FeedEventType.PULL_REQUEST
            TYPE_PUSH -> FeedEventType.PUSH
            TYPE_WATCH -> FeedEventType.STAR
            TYPE_FORK -> FeedEventType.FORK
            else -> return null
        }
    val repoUrl = "https://github.com/${repo.name}"
    return FeedItem(
        id = id,
        type = eventType,
        actorLogin = actor.login,
        actorAvatarUrl = actor.avatarUrl,
        repoFullName = repo.name,
        action = payload?.action,
        title = titleOf(eventType),
        number = numberOf(eventType),
        commitCount = if (eventType == FeedEventType.PUSH) payload?.size else null,
        createdAt = createdAt,
        htmlUrl = htmlUrlOf(eventType, repoUrl),
    )
}

/** 条目标题：issue/PR 标题；push 首条提交信息；star/fork 空串（UI 层不展示标题行） */
private fun EventDto.titleOf(eventType: FeedEventType): String =
    when (eventType) {
        FeedEventType.ISSUE, FeedEventType.ISSUE_COMMENT -> {
            payload?.issue?.title.orEmpty()
        }

        FeedEventType.PULL_REQUEST -> {
            payload?.pullRequest?.title.orEmpty()
        }

        FeedEventType.PUSH -> {
            payload
                ?.commits
                ?.firstOrNull()
                ?.message
                .orEmpty()
        }

        FeedEventType.STAR, FeedEventType.FORK -> {
            ""
        }
    }

/** issue/PR 编号；其余类型 null */
private fun EventDto.numberOf(eventType: FeedEventType): Int? =
    when (eventType) {
        FeedEventType.ISSUE, FeedEventType.ISSUE_COMMENT -> payload?.issue?.number
        FeedEventType.PULL_REQUEST -> payload?.pullRequest?.number
        else -> null
    }

/**
 * 点击导航链接：issue/PR 用内容页链接，其余用仓库链接。
 *
 * IssueComment 优先取 issue.html_url：comment.html_url 带 `#issuecomment-*` fragment，
 * GitHubLinkParser 无法解析（片段粘在编号段上 → External），会丢失应用内导航。
 */
private fun EventDto.htmlUrlOf(
    eventType: FeedEventType,
    repoUrl: String,
): String =
    when (eventType) {
        FeedEventType.ISSUE -> payload?.issue?.htmlUrl ?: repoUrl
        FeedEventType.ISSUE_COMMENT -> payload?.issue?.htmlUrl ?: payload?.comment?.htmlUrl ?: repoUrl
        FeedEventType.PULL_REQUEST -> payload?.pullRequest?.htmlUrl ?: repoUrl
        else -> repoUrl
    }
