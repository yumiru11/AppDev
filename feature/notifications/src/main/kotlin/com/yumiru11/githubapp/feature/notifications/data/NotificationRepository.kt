@file:OptIn(ExperimentalCoroutinesApi::class)

package com.yumiru11.githubapp.feature.notifications.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知数据仓库：Paging 分页 + 已读操作（T19 验收第 2 条状态同步）。
 *
 * 已读同步机制：PATCH 成功后 bump [refreshSignal] → Pager 重建 → 服务端重新拉取
 * （已读条目从默认列表消失，正在展示的列表自动刷新）。
 */
@Singleton
class NotificationRepository
    @Inject
    constructor(
        private val notificationApi: NotificationApi,
    ) {
        private val refreshSignal = MutableStateFlow(0)

        /** 通知分页流：过滤切换/已读刷新都会重建 Pager（新 PagingSource → 新请求） */
        fun notifications(filter: NotificationFilter): Flow<PagingData<NotificationItem>> =
            refreshSignal.flatMapLatest {
                Pager(
                    config = PagingConfig(pageSize = PAGE_SIZE),
                    pagingSourceFactory = { NotificationsPagingSource(notificationApi, filter) },
                ).flow
            }

        /** 标记单条已读（成功后触发列表刷新） */
        suspend fun markRead(threadId: String) {
            notificationApi.markThreadRead(threadId)
            refreshSignal.update { it + 1 }
        }

        /** 标记全部已读（成功后触发列表刷新） */
        suspend fun markAllRead() {
            notificationApi.markAllRead()
            refreshSignal.update { it + 1 }
        }

        private companion object {
            const val PAGE_SIZE = 30
        }
    }
