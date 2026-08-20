@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底，T15 细化异常类型

package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PR 详情页 ViewModel（T15）。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo]/[number] 导航参数，加载 PR 详情与四 Tab 数据。
 * 详情先行（Checks 需 head sha），随后时间线/提交/文件/Checks 并行加载（all-or-nothing）。
 *
 * 状态机（可单测）：
 * - [selectedTab]：四 Tab 切换（Conversation/Commits/Checks/Files）
 * - [expandedCheckIds]：Checks 失败详情展开集合
 * - [expandedCommitShas]：Commits 展开 diff 摘要集合
 * - [expandedFileNames]：Files changed 展开 patch 集合
 *
 * 错误一律映射为 [PullRequestErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 */
@HiltViewModel
class PullRequestDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PullRequestRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])
        private val number: Int = checkNotNull(savedStateHandle["number"])

        private val _uiState = MutableStateFlow<PullRequestDetailUiState>(PullRequestDetailUiState.Loading)
        val uiState: StateFlow<PullRequestDetailUiState> = _uiState.asStateFlow()

        private val _selectedTab = MutableStateFlow(PullRequestTab.CONVERSATION)
        val selectedTab: StateFlow<PullRequestTab> = _selectedTab.asStateFlow()

        private val _expandedCheckIds = MutableStateFlow<Set<Long>>(emptySet())
        val expandedCheckIds: StateFlow<Set<Long>> = _expandedCheckIds.asStateFlow()

        private val _expandedCommitShas = MutableStateFlow<Set<String>>(emptySet())
        val expandedCommitShas: StateFlow<Set<String>> = _expandedCommitShas.asStateFlow()

        private val _expandedFileNames = MutableStateFlow<Set<String>>(emptySet())
        val expandedFileNames: StateFlow<Set<String>> = _expandedFileNames.asStateFlow()

        init {
            loadPullRequestDetail()
        }

        fun retry() {
            _uiState.value = PullRequestDetailUiState.Loading
            loadPullRequestDetail()
        }

        /** 切换四 Tab（幂等：同 Tab 不重复发射） */
        fun selectTab(tab: PullRequestTab) {
            if (_selectedTab.value == tab) return
            _selectedTab.value = tab
        }

        /** 展开/收起 Check Run 失败详情 */
        fun toggleCheckExpanded(checkId: Long) {
            val current = _expandedCheckIds.value
            _expandedCheckIds.value = if (checkId in current) current - checkId else current + checkId
        }

        /** 展开/收起提交 diff 摘要 */
        fun toggleCommitExpanded(sha: String) {
            val current = _expandedCommitShas.value
            _expandedCommitShas.value = if (sha in current) current - sha else current + sha
        }

        /** 展开/收起文件 patch */
        fun toggleFileExpanded(filename: String) {
            val current = _expandedFileNames.value
            _expandedFileNames.value = if (filename in current) current - filename else current + filename
        }

        private fun loadPullRequestDetail() {
            viewModelScope.launch {
                _uiState.value = PullRequestDetailUiState.Loading
                try {
                    val pullRequest = repository.getPullRequest(owner, repo, number)
                    val headSha = pullRequest.head?.sha
                    val bundle =
                        coroutineScope {
                            val timelineDeferred = async { repository.timeline(owner, repo, number) }
                            val commitsDeferred = async { repository.commits(owner, repo, number) }
                            val filesDeferred = async { repository.files(owner, repo, number) }
                            val checksDeferred =
                                async {
                                    headSha?.let { repository.checkRuns(owner, repo, it) } ?: emptyList()
                                }
                            val statusDeferred =
                                async {
                                    headSha?.let { repository.combinedStatus(owner, repo, it) }
                                }
                            TimelineBundle(
                                timeline = timelineDeferred.await(),
                                commits = commitsDeferred.await(),
                                files = filesDeferred.await(),
                                checkRuns = checksDeferred.await(),
                                combinedStatus = statusDeferred.await(),
                            )
                        }
                    _uiState.value =
                        PullRequestDetailUiState.Success(
                            pullRequest = pullRequest,
                            timeline = bundle.timeline,
                            commits = bundle.commits,
                            files = bundle.files,
                            checkRuns = bundle.checkRuns,
                            combinedStatus = bundle.combinedStatus,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = PullRequestDetailUiState.Error(errorType = e.toPullRequestErrorType())
                }
            }
        }
    }

/** 并行加载结果聚合（避免 data class 五元组样板） */
private data class TimelineBundle(
    val timeline: List<com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem>,
    val commits: List<com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit>,
    val files: List<com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile>,
    val checkRuns: List<com.yumiru11.githubapp.feature.pullrequest.model.CheckRun>,
    val combinedStatus: com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus?,
)
