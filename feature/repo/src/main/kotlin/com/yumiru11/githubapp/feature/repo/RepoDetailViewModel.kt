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
import javax.inject.Inject

/**
 * 仓库详情页 ViewModel。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo] 导航参数，加载仓库元数据与 README。
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
                    _uiState.value =
                        RepoDetailUiState.Error(
                            message = e.message ?: "Unknown error",
                        )
                }
            }
        }

        private suspend fun loadReadme() {
            val currentState = _uiState.value
            if (currentState !is RepoDetailUiState.Success) return

            val themeVersion = MarkdownThemeTokens.versionHash()
            val result = repoRepository.getReadmeHtml(owner, repo, themeVersion)
            val newReadmeState =
                result.fold(
                    onSuccess = { html ->
                        if (html.isBlank()) {
                            ReadmeState.Empty
                        } else {
                            ReadmeState.Loaded(html, ReadmeRenderMode.WEBVIEW)
                        }
                    },
                    onFailure = { e ->
                        ReadmeState.Error(e.message ?: "Failed to load README")
                    },
                )
            _uiState.value = currentState.copy(readmeState = newReadmeState)
        }
    }
