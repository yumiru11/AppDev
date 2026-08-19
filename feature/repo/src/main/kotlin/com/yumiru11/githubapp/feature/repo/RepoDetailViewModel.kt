@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底，T14 细化异常类型

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.markdown.webview.MarkdownThemeTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 仓库详情页 ViewModel。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo] 导航参数，加载仓库元数据与 README。
 * 错误一律映射为 [RepoErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 */
@HiltViewModel
class RepoDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repoRepository: RepoRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])

        private val _uiState = MutableStateFlow<RepoDetailUiState>(RepoDetailUiState.Loading)
        val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

        init {
            loadRepoDetail()
        }

        fun retry() {
            _uiState.value = RepoDetailUiState.Loading
            loadRepoDetail()
        }

        private fun loadRepoDetail() {
            viewModelScope.launch {
                _uiState.value = RepoDetailUiState.Loading
                try {
                    val repository = repoRepository.getRepository(owner, repo)
                    _uiState.value =
                        RepoDetailUiState.Success(
                            repo = repository,
                            readmeState = ReadmeState.Loading,
                        )
                    loadReadme()
                } catch (e: Exception) {
                    _uiState.value = RepoDetailUiState.Error(errorType = mapError(e))
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

        /** 异常 → 错误类型（404 → NOT_FOUND，IO → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): RepoErrorType =
            when {
                e is HttpException && e.code() == 404 -> RepoErrorType.NOT_FOUND
                e is IOException -> RepoErrorType.NETWORK
                else -> RepoErrorType.UNKNOWN
            }
    }
