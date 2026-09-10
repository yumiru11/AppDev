@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// 星标写操作网络/IO 错误统一兜底 → Snackbar 事件（同 RepoDetailViewModel 先例）：
// 异常类型对用户没有可操作差异，UI 只需"失败 → 提示重试"，故有意不向上传播也不记日志。

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.datastore.model.RepoLayoutMode
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubdata.paging.ViewerRepositoriesPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

/**
 * 「仓库」大分区（底部导航中间 Tab）ViewModel（#166 / UI01+UI02）。
 *
 * 此前该分区是 [com.yumiru11.githubapp.core.ui.PlaceholderScreen] 占位——三个底部 Tab
 * 里有一个点进去是空壳，属于功能与观感双重缺口。本次落地 ui-design §3.2 的完整形态：
 *
 * - **数据**：当前登录用户的仓库，GraphQL 游标分页（[ViewerRepositoriesPagingSource]，
 *   T5 已建）；游客态不触网，直接给登录引导
 * - **布局切换（UI01）**：通栏 / 网格两态，右上角按钮单击翻转，**持久化**到偏好
 *   （切了又弹回去是列表页最常见的体验投诉）；纯翻转逻辑收敛在
 *   [RepoLayoutMode.toggled]，单测覆盖
 * - **长按菜单（UI02）**：复制链接 / 浏览器打开 / 分享 / 收藏切换。前三个纯客户端动作
 *   不依赖网络；收藏是写操作，走「先查后写 + 结果 Snackbar」，失败不改变本地状态
 *   （列表项本身不携带 star 态，故不做乐观改色——宁可少一个视觉反馈，也不要显示错误状态）
 */
@HiltViewModel
class ReposViewModel
    @Inject
    constructor(
        private val preferences: UserPreferencesRepository,
        private val managementRepository: RepoManagementRepository,
        private val sessionManager: OAuthSessionManager,
        private val pagingSourceProvider: Provider<ViewerRepositoriesPagingSource>,
    ) : ViewModel() {
        /** 当前用户仓库分页流。Pager 冷启动不触网；cachedIn 保证配置变更/重组的缓存复用。 */
        val repositories: Flow<PagingData<Repository>> =
            Pager(
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        initialLoadSize = PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                pagingSourceFactory = { pagingSourceProvider.get() },
            ).flow
                .cachedIn(viewModelScope)

        /** 列表布局（偏好持久化；首帧用默认值避免闪烁） */
        val layout: StateFlow<RepoLayoutMode> =
            preferences.repoLayout.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = RepoLayoutMode.LIST,
            )

        /** 游客态（未登录）→ UI 展示登录引导而非空列表 */
        val isAnonymous: StateFlow<Boolean> =
            sessionManager.authState
                .map { it is AuthState.Anonymous }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = false,
                )

        private val _events = MutableSharedFlow<ReposEvent>(extraBufferCapacity = EVENT_BUFFER)
        val events: SharedFlow<ReposEvent> = _events.asSharedFlow()

        /** 布局切换（UI01）：通栏 ⇄ 网格，落盘后由 [layout] 回流。 */
        fun toggleLayout() {
            viewModelScope.launch {
                preferences.setRepoLayout(layout.value.toggled())
            }
        }

        /**
         * 收藏 / 取消收藏（UI02 长按菜单）。先查真实状态再写，避免"点了没反应/反向操作"。
         * 成功/失败都发 [ReposEvent]，UI 用 Snackbar 反馈。
         */
        fun toggleStar(
            owner: String,
            name: String,
        ) {
            viewModelScope.launch {
                try {
                    val starred = managementRepository.isStarred(owner, name)
                    managementRepository.setStarred(owner, name, !starred)
                    _events.emit(ReposEvent.StarToggled(repository = "$owner/$name", starred = !starred))
                } catch (e: Exception) {
                    _events.emit(ReposEvent.StarFailed)
                }
            }
        }

        private companion object {
            /** GitHub 单页条数上限 100；30 兼顾流量与滚动流畅（与个人页仓库列表同口径） */
            const val PAGE_SIZE = 30
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** 事件通道容量：Snackbar 事件不需要排队堆积，1 足够（extraBufferCapacity 保证 tryEmit 不挂） */
            const val EVENT_BUFFER = 1
        }
    }

/** 「仓库」分区的用户可见事件（UI 侧映射为 Snackbar 文案）。 */
sealed interface ReposEvent {
    /** 收藏状态已变更 */
    data class StarToggled(
        val repository: String,
        val starred: Boolean,
    ) : ReposEvent

    /** 收藏切换失败（网络/权限） */
    data object StarFailed : ReposEvent
}
