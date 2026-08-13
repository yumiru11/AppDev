package com.yumiru11.githubapp.feature.notifications.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import com.yumiru11.githubapp.core.githubrest.model.NotificationDto
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import retrofit2.HttpException
import java.io.IOException

/**
 * 通知分页数据源：按 [filter] 请求 GET /notifications 并映射为领域模型。
 *
 * 错误语义：网络/HTTP 错误一律转 [LoadResult.Error]（Paging 生命周期内呈现，
 * UI 层 LazyPagingItems.loadState 驱动错误态与重试）。
 */
class NotificationsPagingSource(
    private val notificationApi: NotificationApi,
    private val filter: NotificationFilter,
) : PagingSource<Int, NotificationItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, NotificationItem> =
        try {
            val page = params.key ?: STARTING_PAGE
            val items =
                notificationApi
                    .listNotifications(
                        all = filter.allParam,
                        participating = filter.participatingParam,
                        page = page,
                        perPage = params.loadSize,
                    ).map(NotificationDto::toDomain)

            LoadResult.Page(
                data = if (filter == NotificationFilter.MENTION) items.filter { it.isMention } else items,
                prevKey = if (page > STARTING_PAGE) page - 1 else null,
                // 以原始页大小决定是否还有下一页（客户端 mention 过滤不截断分页）
                nextKey = if (items.isNotEmpty()) page + 1 else null,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Int, NotificationItem>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { closestPage ->
                closestPage.prevKey?.plus(1) ?: closestPage.nextKey?.minus(1)
            }
        }

    private companion object {
        const val STARTING_PAGE = 1
    }
}

/** 服务端参数映射：PARTICIPATING 不带 all；其余（ALL/MENTION）带 all=true */
private val NotificationFilter.allParam: Boolean?
    get() = if (this == NotificationFilter.PARTICIPATING) null else true

/** 服务端参数映射：仅 PARTICIPATING 带 participating=true */
private val NotificationFilter.participatingParam: Boolean?
    get() = if (this == NotificationFilter.PARTICIPATING) true else null

/** mention 过滤：reason 为 mention 或 team_mention（GitHub 无服务端 mention 参数） */
private val NotificationItem.isMention: Boolean
    get() = reason == REASON_MENTION || reason == REASON_TEAM_MENTION

private fun NotificationDto.toDomain(): NotificationItem =
    NotificationItem(
        id = id,
        repoFullName = repository.fullName.orEmpty(),
        subjectTitle = subject.title,
        subjectType = subject.type,
        reason = reason,
        unread = unread,
        updatedAt = updatedAt,
        htmlUrl = htmlUrl,
    )

private const val REASON_MENTION = "mention"
private const val REASON_TEAM_MENTION = "team_mention"
