package com.yumiru11.githubapp.feature.home.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubrest.api.EventsApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.feature.home.model.FeedItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首页动态流数据仓库（T10）：当前用户 login 获取 + received_events 分页流。
 *
 * 下拉刷新由 UI 层 LazyPagingItems.refresh() 触发（Paging invalidate 重建请求），
 * 仓库无需维护刷新信号（与 NotificationRepository 的已读刷新场景不同）。
 */
@Singleton
class FeedRepository
    @Inject
    constructor(
        private val eventsApi: EventsApi,
        private val userApi: UserApi,
    ) {
        /** 当前登录用户 login（动态流请求路径参数；未登录时 UI 已拦截不会走到这里） */
        suspend fun currentLogin(): String = userApi.currentUser().login

        /** 动态分页流：按 [login] 拉取 received_events */
        fun feed(login: String): Flow<PagingData<FeedItem>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                pagingSourceFactory = { FeedPagingSource(eventsApi, login) },
            ).flow

        private companion object {
            const val PAGE_SIZE = 30
        }
    }
