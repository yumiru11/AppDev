@file:Suppress("TooGenericExceptionCaught")
// 选择器加载异常统一兜底为 Error 态（同 HomeViewModel 先例）

package com.yumiru11.githubapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.home.data.UserReposRepository
import com.yumiru11.githubapp.feature.home.model.RepoOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/** 仓库选择器 UI 状态（#89）：Loading / Ready / Error 三态，复用 [HomeErrorType] 文案映射。 */
sealed interface RepoPickerUiState {
    data object Loading : RepoPickerUiState

    data class Ready(
        val repos: List<RepoOption>,
    ) : RepoPickerUiState

    data class Error(
        val errorType: HomeErrorType,
    ) : RepoPickerUiState
}

/**
 * 仓库选择器 ViewModel（#89）：面板首次组合即加载当前用户仓库；失败可重试。
 */
@HiltViewModel
class RepoPickerViewModel
    @Inject
    constructor(
        private val reposRepository: UserReposRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<RepoPickerUiState>(RepoPickerUiState.Loading)
        val uiState: StateFlow<RepoPickerUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        /** 错误态重试 */
        fun retry() {
            if (_uiState.value is RepoPickerUiState.Error) {
                load()
            }
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = RepoPickerUiState.Loading
                try {
                    _uiState.value = RepoPickerUiState.Ready(reposRepository.currentUserRepos())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value =
                        RepoPickerUiState.Error(errorType = mapError(e))
                }
            }
        }

        private fun mapError(e: Throwable): HomeErrorType =
            if (e is IOException || e is HttpException) HomeErrorType.NETWORK else HomeErrorType.UNKNOWN
    }
