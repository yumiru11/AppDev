package com.yumiru11.githubapp.feature.notifications.data

import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import com.yumiru11.githubapp.core.githubrest.model.NotificationDto
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知数据仓库（#88 面板形态）：一次性快照读取 + 已读/done 写操作。
 *
 * - [latest]：面板打开时按页直连 [NotificationApi] 拉快照（首页
 *   per_page=[PANEL_PAGE_SIZE]，短页即止，上限 [PANEL_MAX_PAGES] 页防失控）；
 *   服务端参数映射与 mention 客户端过滤语义与 T19 分页流一致（原分页栈随面板化移除）
 * - 写操作：PATCH 已读 / PATCH 全部已读 / DELETE done。面板侧做乐观更新，
 *   写失败由 ViewModel 重拉快照对齐服务端真相（仓库层保持无状态透传，T19 约定延续）
 */
@Singleton
class NotificationRepository
    @Inject
    constructor(
        private val notificationApi: NotificationApi,
    ) {
        /** 面板用一次性通知快照：按 [filter] 拉取至多 [PANEL_MAX_PAGES] 页并合并 */
        suspend fun latest(filter: NotificationFilter): List<NotificationItem> {
            val collected = mutableListOf<NotificationItem>()
            var page = STARTING_PAGE
            repeat(PANEL_MAX_PAGES) {
                val items =
                    notificationApi
                        .listNotifications(
                            all = filter.allParam,
                            participating = filter.participatingParam,
                            page = page,
                            perPage = PANEL_PAGE_SIZE,
                        ).let { raw ->
                            if (filter == NotificationFilter.MENTION) {
                                raw.filter { dto -> dto.toDomain().isMention }
                            } else {
                                raw
                            }
                        }.map(NotificationDto::toDomain)
                collected += items
                if (items.size < PANEL_PAGE_SIZE) return collected // 短页即服务端末页，停止翻页
                page += 1
            }
            return collected
        }

        /** 标记单条已读（PATCH thread） */
        suspend fun markRead(threadId: String) {
            notificationApi.markThreadRead(threadId)
        }

        /** 标记全部已读（PATCH notifications） */
        suspend fun markAllRead() {
            notificationApi.markAllRead()
        }

        /** 标记单条 done（DELETE thread；#88 面板左滑删除） */
        suspend fun markDone(threadId: String) {
            notificationApi.markThreadDone(threadId)
        }

        private companion object {
            const val STARTING_PAGE = 1

            /** GitHub /notifications 单页上限 100 */
            const val PANEL_PAGE_SIZE = 100

            /** 面板最多拉取页数（≤200 条足够面板场景，防御性上限） */
            const val PANEL_MAX_PAGES = 2
        }
    }

/** 服务端参数映射：PARTICIPATING 不带 all；其余（ALL/MENTION）带 all=true（T19 语义） */
private val NotificationFilter.allParam: Boolean?
    get() = if (this == NotificationFilter.PARTICIPATING) null else true

/** 服务端参数映射：仅 PARTICIPATING 带 participating=true（T19 语义） */
private val NotificationFilter.participatingParam: Boolean?
    get() = if (this == NotificationFilter.PARTICIPATING) true else null

/** mention 过滤：reason 为 mention 或 team_mention（GitHub 无服务端 mention 参数，T19 语义） */
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
