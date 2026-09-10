@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：网络/IO 错误统一兜底（同 RepoDetailViewModel 先例）
// - SwallowedException：关注态的**读取**失败按设计降级为「未关注」按钮文案（资料头已到手，
//   不因一次 403/抖动把整页打成错误态）；关注**写操作**失败则回滚乐观更新并经事件通道
//   Snackbar 告知用户（ProfileEvent.FollowActionFailed），异常对象本身不作为用户可见信息。

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
 * - 模式（L10）：[login] 为 null = 本人主页（设置入口等写能力保留）；
 *   非 null = 他人主页（只读资料头 + 关注按钮）。关注走乐观更新 + 失败回滚 + 事件通道提示。
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

        /** 本人主页（PROFILE 路由）/ 他人主页（USER 路由，L10） */
        val isSelf: Boolean = login == null

        private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        /** 一次性事件（关注失败提示）；Channel 保证只被消费一次 */
        private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
        val events: Flow<ProfileEvent> = _events.receiveAsFlow()

        /** 关注写操作在途标志：连点只发一次请求（乐观更新已即时改文案） */
        private var followInFlight = false

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

        /**
         * 关注/取关（L10 他人主页）：乐观更新 → 失败回滚 + 事件提示。
         *
         * 本人主页无关注语义（UI 也不渲染按钮），此处防御性忽略。
         */
        fun toggleFollow() {
            val target = login ?: return
            val current = _uiState.value as? ProfileUiState.Success ?: return
            if (current.isSelf || followInFlight) return

            val following = !current.isFollowing
            followInFlight = true
            _uiState.value = current.copy(isFollowing = following, user = current.user.withFollowersDelta(following))
            viewModelScope.launch {
                try {
                    if (following) {
                        profileRepository.follow(target)
                    } else {
                        profileRepository.unfollow(target)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 回滚到操作前快照（含关注者数），并提示用户
                    _uiState.value = current
                    _events.send(ProfileEvent.FollowActionFailed)
                } finally {
                    followInFlight = false
                }
            }
        }

        private fun loadProfile() {
            viewModelScope.launch {
                _uiState.value = ProfileUiState.Loading
                try {
                    val user = profileRepository.getProfile(login)
                    _uiState.value =
                        ProfileUiState.Success(
                            user = user,
                            isSelf = isSelf,
                            isFollowing = fetchFollowState(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = ProfileUiState.Error(mapError(e))
                }
            }
        }

        /**
         * 他人主页的初始关注态（204/404）。
         *
         * 查询失败（403 限流 / 网络抖动）不把整页打成错误态——资料头已经拿到，
         * 降级为「未关注」按钮文案（点击关注是幂等的，不会造成错误状态）。
         */
        private suspend fun fetchFollowState(): Boolean {
            val target = login ?: return false
            return try {
                profileRepository.isFollowing(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
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

        /** 关注/取关后的关注者数增减（乐观更新用；负数防御性收敛到 0） */
        private fun User.withFollowersDelta(following: Boolean): User =
            copy(followers = (followers + if (following) 1 else -1).coerceAtLeast(0))

        private companion object {
            /** GitHub 单页条数上限（REST per_page 最大 100，30 兼顾流量与滚动体验） */
            const val PAGE_SIZE = 30
        }
    }
