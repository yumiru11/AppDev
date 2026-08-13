@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底（同 RepoDetailViewModel 先例）

package com.yumiru11.githubapp.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 个人页 ViewModel（T20）。
 *
 * - 路由语义：PROFILE 路由无 login 参数（[login] = null → 当前认证用户）；
 *   USER 路由带 login 参数（公开用户）。
 * - 登录态：收集 [OAuthSessionManager.authState]，Anonymous → [ProfileUiState.Anonymous]
 *   显式未登录态（UI 展示登录引导）；SignedIn/PAT → 加载资料头。
 * - 四列表（Repos/Starred/Followers/Following）为独立 PagingData 流，cachedIn 共享缓存；
 *   Pager 冷启动不触网，UI 仅在 Success 分支收集。
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val profileRepository: ProfileRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        /** USER 路由传入的 login；PROFILE 路由为 null（当前用户）。 */
        private val login: String? = savedStateHandle["login"]

        private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        /** 当前用户仓库（分页） */
        val repositories: Flow<PagingData<Repository>> =
            pager { profileRepository.repositories(login) }

        /** Starred 仓库（分页） */
        val starred: Flow<PagingData<Repository>> =
            pager { profileRepository.starred(login) }

        /** 关注者（分页） */
        val followers: Flow<PagingData<User>> =
            pager { profileRepository.followers(login) }

        /** 关注中（分页） */
        val following: Flow<PagingData<User>> =
            pager { profileRepository.following(login) }

        init {
            viewModelScope.launch {
                sessionManager.authState.collect { state ->
                    when (state) {
                        is AuthState.Anonymous -> {
                            _uiState.value = ProfileUiState.Anonymous
                        }

                        else -> {
                            loadProfile()
                        }
                    }
                }
            }
        }

        /** 资料头加载失败后重试。 */
        fun retry() {
            loadProfile()
        }

        private fun loadProfile() {
            viewModelScope.launch {
                _uiState.value = ProfileUiState.Loading
                try {
                    val user = profileRepository.getProfile(login)
                    _uiState.value = ProfileUiState.Success(user)
                } catch (e: Exception) {
                    _uiState.value = ProfileUiState.Error(mapError(e))
                }
            }
        }

        /** 异常 → 错误类型（404 → NOT_FOUND，IO → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): ProfileErrorType =
            when {
                e is HttpException && e.code() == 404 -> ProfileErrorType.NOT_FOUND
                e is IOException -> ProfileErrorType.NETWORK
                else -> ProfileErrorType.UNKNOWN
            }

        private fun <T : Any> pager(source: () -> androidx.paging.PagingSource<Int, T>): Flow<PagingData<T>> =
            androidx.paging
                .Pager(
                    config =
                        PagingConfig(
                            pageSize = PAGE_SIZE,
                            initialLoadSize = PAGE_SIZE,
                            enablePlaceholders = false,
                        ),
                    pagingSourceFactory = source,
                ).flow
                .cachedIn(viewModelScope)

        private companion object {
            /** GitHub 单页条数上限（REST per_page 最大 100，30 兼顾流量与滚动体验） */
            const val PAGE_SIZE = 30
        }
    }
