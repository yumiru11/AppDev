package com.yumiru11.githubapp.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yumiru11.githubapp.feature.profile.model.GistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Gist 列表 ViewModel（L11 / ui-design.md §3.7）。
 *
 * - 路由参数 `username` 经 SavedStateHandle 注入（[com.yumiru11.githubapp.core.navigation.AppRoute.Gists]）
 * - 列表是独立 PagingData 流（cachedIn 共享缓存），冷启动不触网，加载/错误由 UI 层 loadState 呈现
 */
@HiltViewModel
class GistsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        profileRepository: ProfileRepository,
    ) : ViewModel() {
        /** Gist 归属用户（本人主页与他人主页入口共用） */
        val username: String = savedStateHandle.get<String>(USERNAME_ARG).orEmpty()

        /** 分页 Gist 列表 */
        val gists: Flow<PagingData<GistItem>> =
            Pager(
                config =
                    PagingConfig(
                        pageSize = PAGE_SIZE,
                        initialLoadSize = PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                pagingSourceFactory = { profileRepository.gists(username) },
            ).flow
                .cachedIn(viewModelScope)

        companion object {
            /** 路由参数键（= AppRoute.Gists 的属性名，Navigation 依此写入 SavedStateHandle） */
            const val USERNAME_ARG = "username"

            private const val PAGE_SIZE = 30
        }
    }
