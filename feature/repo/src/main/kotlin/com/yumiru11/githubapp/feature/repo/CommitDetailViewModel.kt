@file:Suppress("TooGenericExceptionCaught")

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * COMMIT 详情页 ViewModel（L09）。
 *
 * - 从 SavedStateHandle 读 [owner]/[repo]/[sha] 导航参数，加载提交详情（文件列表跨页累积）
 * - 点击文件 → 页内解析 patch（[CommitDiffParser]）并切换到 diff 视图；返回 → 回文件列表
 * - 错误一律映射 [RepoErrorType]（UI 层 stringResource 本地化）
 */
@HiltViewModel
class CommitDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val commitRepository: CommitRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])
        private val sha: String = checkNotNull(savedStateHandle["sha"])

        private val _uiState = MutableStateFlow<CommitDetailUiState>(CommitDetailUiState.Loading)
        val uiState: StateFlow<CommitDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        /** 重试（错误态）。 */
        fun retry() {
            _uiState.value = CommitDetailUiState.Loading
            load()
        }

        /** 点击文件 → 解析该文件 patch 并进入 diff 视图。 */
        fun selectFile(file: CommitFile) {
            val state = _uiState.value as? CommitDetailUiState.Success ?: return
            _uiState.value =
                state.copy(
                    selectedFile = file,
                    diffLines = CommitDiffParser.parse(file.patch),
                )
        }

        /** 返回文件列表。 */
        fun clearSelection() {
            val state = _uiState.value as? CommitDetailUiState.Success ?: return
            _uiState.value = state.copy(selectedFile = null, diffLines = emptyList())
        }

        private fun load() {
            viewModelScope.launch {
                commitRepository
                    .getCommit(owner, repo, sha)
                    .fold(
                        onSuccess = { detail -> _uiState.value = CommitDetailUiState.Success(commit = detail) },
                        onFailure = { e -> _uiState.value = CommitDetailUiState.Error(mapError(e)) },
                    )
            }
        }

        /** 异常 → 错误类型（401/403 → FORBIDDEN，404 → NOT_FOUND，IO → NETWORK，其余 → UNKNOWN） */
        private fun mapError(e: Throwable): RepoErrorType =
            when {
                e is HttpException && (e.code() == 401 || e.code() == 403) -> RepoErrorType.FORBIDDEN
                e is HttpException && e.code() == 404 -> RepoErrorType.NOT_FOUND
                e is IOException -> RepoErrorType.NETWORK
                else -> RepoErrorType.UNKNOWN
            }
    }
