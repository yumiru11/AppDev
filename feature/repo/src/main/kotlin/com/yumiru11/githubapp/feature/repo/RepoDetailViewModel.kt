@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // 网络/IO 错误统一兜底（T14 细化异常类型）；Star/Watch 失败回滚后异常即事件，无需再抛/记日志

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.markdown.webview.MarkdownThemeTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 仓库详情页 ViewModel。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo] 导航参数，加载仓库元数据与 README。
 * 错误一律映射为 [RepoErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 *
 * T12 仓库管理：
 * - 登录态经 [OAuthSessionManager.authState] 收集（游客只读：隐藏操作按钮）
 * - Star/Watch 乐观更新 → 失败回滚 + [RepoEvent.ToggleFailed]
 * - Fork 失败按 HttpException 码映射事件（403 无权限 / 422 已 Fork）
 * - Releases/Tags 第三个 Tab 懒加载；Release 详情页内展开（不进导航）
 * - 语言栏数据（Linguist）随仓库加载，失败静默（非关键信息）
 */
@HiltViewModel
class RepoDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repoRepository: RepoRepository,
        private val repoManagementRepository: RepoManagementRepository,
        private val sessionManager: OAuthSessionManager,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _uiState = MutableStateFlow<RepoDetailUiState>(RepoDetailUiState.Loading)
        val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

        private val _events = Channel<RepoEvent>(Channel.BUFFERED)
        val events: Flow<RepoEvent> = _events.receiveAsFlow()

        init {
            observeAuthState()
            loadRepoDetail()
        }

        fun retry() {
            _uiState.value = RepoDetailUiState.Loading
            loadRepoDetail()
        }

        /** 切换 Star 状态（乐观更新 → 失败回滚 + 事件）。游客/操作进行中忽略。 */
        fun toggleStar() {
            val state = _uiState.value as? RepoDetailUiState.Success ?: return
            if (!state.isLoggedIn || state.pendingAction != null) return
            val target = !state.isStarred
            _uiState.value = state.copy(isStarred = target, pendingAction = RepoAction.STAR)
            viewModelScope.launch {
                try {
                    repoManagementRepository.setStarred(owner, repo, target)
                    clearPendingAction()
                } catch (e: Exception) {
                    rollbackToggle(isStarred = !target)
                    _events.send(RepoEvent.ToggleFailed)
                }
            }
        }

        /** 切换 Watch 状态（乐观更新 → 失败回滚 + 事件）。游客/操作进行中忽略。 */
        fun toggleWatch() {
            val state = _uiState.value as? RepoDetailUiState.Success ?: return
            if (!state.isLoggedIn || state.pendingAction != null) return
            val target = !state.isWatching
            _uiState.value = state.copy(isWatching = target, pendingAction = RepoAction.WATCH)
            viewModelScope.launch {
                try {
                    repoManagementRepository.setWatching(owner, repo, target)
                    clearPendingAction()
                } catch (e: Exception) {
                    rollbackToggle(isWatching = !target)
                    _events.send(RepoEvent.ToggleFailed)
                }
            }
        }

        /** Fork 仓库（403 → 无权限；422 → 已 Fork；成功 → Forked 事件）。游客/操作进行中忽略。 */
        fun fork() {
            val state = _uiState.value as? RepoDetailUiState.Success ?: return
            if (!state.isLoggedIn || state.pendingAction != null) return
            _uiState.value = state.copy(pendingAction = RepoAction.FORK)
            viewModelScope.launch {
                val result = repoManagementRepository.fork(owner, repo)
                clearPendingAction()
                result.fold(
                    onSuccess = { _events.send(RepoEvent.Forked) },
                    onFailure = { e -> _events.send(mapForkFailure(e)) },
                )
            }
        }

        /** 第三个 Tab 打开时确保 Releases 已加载（Idle/Error 才触发，Loaded 不重复拉取）。 */
        fun ensureReleasesLoaded() {
            val state = _uiState.value as? RepoDetailUiState.Success ?: return
            if (state.releasesState !is ReleasesState.Idle && state.releasesState !is ReleasesState.Error) return
            _uiState.value = state.copy(releasesState = ReleasesState.Loading)
            viewModelScope.launch {
                repoManagementRepository
                    .getReleases(owner, repo)
                    .onSuccess { releases ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(releasesState = ReleasesState.Loaded(releases)) else s
                        }
                    }.onFailure { e ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(releasesState = ReleasesState.Error(mapError(e))) else s
                        }
                    }
            }
        }

        /** 第三个 Tab 打开时确保 Tags 已加载（Idle/Error 才触发，Loaded 不重复拉取）。 */
        fun ensureTagsLoaded() {
            val state = _uiState.value as? RepoDetailUiState.Success ?: return
            if (state.tagsState !is TagsState.Idle && state.tagsState !is TagsState.Error) return
            _uiState.value = state.copy(tagsState = TagsState.Loading)
            viewModelScope.launch {
                repoManagementRepository
                    .getTags(owner, repo)
                    .onSuccess { tags ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(tagsState = TagsState.Loaded(tags)) else s
                        }
                    }.onFailure { e ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(tagsState = TagsState.Error(mapError(e))) else s
                        }
                    }
            }
        }

        /** 展开 Release 详情（页内展开，不进导航）。 */
        fun loadReleaseDetail(releaseId: Long) {
            val state = _uiState.value as? RepoDetailUiState.Success ?: return
            if (state.releasesState !is ReleasesState.Loaded) return
            _uiState.value =
                state.copy(
                    expandedReleaseId = releaseId,
                    releaseDetailState = ReleaseDetailState.Loading,
                )
            viewModelScope.launch {
                repoManagementRepository
                    .getRelease(owner, repo, releaseId)
                    .onSuccess { release ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(releaseDetailState = ReleaseDetailState.Loaded(release)) else s
                        }
                    }.onFailure { e ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(releaseDetailState = ReleaseDetailState.Error(mapError(e))) else s
                        }
                    }
            }
        }

        /** 收起 Release 详情，回到列表。 */
        fun collapseReleaseDetail() {
            _uiState.update { s ->
                if (s is RepoDetailUiState.Success) {
                    s.copy(expandedReleaseId = null, releaseDetailState = ReleaseDetailState.Idle)
                } else {
                    s
                }
            }
        }

        private fun observeAuthState() {
            viewModelScope.launch {
                sessionManager.authState.collect { auth ->
                    val loggedIn = auth !is AuthState.Anonymous
                    _uiState.update { s ->
                        if (s is RepoDetailUiState.Success) s.copy(isLoggedIn = loggedIn) else s
                    }
                    if (loggedIn) loadStarWatchStatus()
                }
            }
        }

        private fun loadRepoDetail() {
            viewModelScope.launch {
                _uiState.value = RepoDetailUiState.Loading
                try {
                    val repository = repoRepository.getRepository(owner, repo)
                    val loggedIn = sessionManager.authState.value !is AuthState.Anonymous
                    _uiState.value =
                        RepoDetailUiState.Success(
                            repo = repository,
                            readmeState = ReadmeState.Loading,
                            isLoggedIn = loggedIn,
                        )
                    loadReadme()
                    loadLanguages()
                    if (loggedIn) loadStarWatchStatus()
                } catch (e: Exception) {
                    _uiState.value = RepoDetailUiState.Error(errorType = mapError(e))
                }
            }
        }

        /** 登录态加载 Star/Watch 状态（游客跳过，按钮隐藏）。 */
        private fun loadStarWatchStatus() {
            viewModelScope.launch {
                val state = _uiState.value as? RepoDetailUiState.Success ?: return@launch
                val starred = repoManagementRepository.isStarred(owner, repo)
                val watching = repoManagementRepository.isWatching(owner, repo)
                _uiState.update { s ->
                    if (s is RepoDetailUiState.Success) s.copy(isStarred = starred, isWatching = watching) else s
                }
            }
        }

        /** 语言栏数据（Linguist）。失败静默：非关键信息，不打断页面。 */
        private fun loadLanguages() {
            viewModelScope.launch {
                repoManagementRepository
                    .getLanguages(owner, repo)
                    .onSuccess { languages ->
                        _uiState.update { s ->
                            if (s is RepoDetailUiState.Success) s.copy(languages = languages) else s
                        }
                    }
            }
        }

        private suspend fun loadReadme() {
            val currentState = _uiState.value
            if (currentState !is RepoDetailUiState.Success) return

            val themeVersion = MarkdownThemeTokens.versionHash()
            val result = repoRepository.getReadme(owner, repo, themeVersion)
            val newReadmeState =
                result.fold(
                    onSuccess = { content ->
                        if (content.html.isNullOrBlank()) {
                            ReadmeState.Empty
                        } else {
                            ReadmeState.Loaded(
                                content = content.html,
                                renderMode = ReadmeRenderMode.WEBVIEW,
                                webViewRenderMode = content.webViewRenderMode,
                            )
                        }
                    },
                    onFailure = { e ->
                        if (e is HttpException && e.code() == 404) {
                            // 仓库无 README（GitHub 404）→ 空态
                            ReadmeState.Empty
                        } else {
                            ReadmeState.Error(errorType = mapError(e))
                        }
                    },
                )
            _uiState.value = currentState.copy(readmeState = newReadmeState)
        }

        private fun clearPendingAction() {
            _uiState.update { s ->
                if (s is RepoDetailUiState.Success) s.copy(pendingAction = null) else s
            }
        }

        /** 回滚 Star/Watch 乐观更新（只回滚对应字段，避免覆盖并发状态）。 */
        private fun rollbackToggle(
            isStarred: Boolean? = null,
            isWatching: Boolean? = null,
        ) {
            _uiState.update { s ->
                if (s is RepoDetailUiState.Success) {
                    s.copy(
                        isStarred = isStarred ?: s.isStarred,
                        isWatching = isWatching ?: s.isWatching,
                        pendingAction = null,
                    )
                } else {
                    s
                }
            }
        }

        /** Fork 失败 → 事件类型（403 无权限 / 422 已 Fork / 其余通用失败）。 */
        private fun mapForkFailure(e: Throwable): RepoEvent =
            when {
                e is HttpException && e.code() == 403 -> RepoEvent.ForkPermissionDenied
                e is HttpException && e.code() == 422 -> RepoEvent.ForkAlreadyExists
                else -> RepoEvent.ForkFailed
            }

        /** 异常 → 错误类型（404 → NOT_FOUND，IO → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): RepoErrorType =
            when {
                e is HttpException && (e.code() == 401 || e.code() == 403) -> RepoErrorType.FORBIDDEN
                e is HttpException && e.code() == 404 -> RepoErrorType.NOT_FOUND
                e is IOException -> RepoErrorType.NETWORK
                else -> RepoErrorType.UNKNOWN
            }
    }
