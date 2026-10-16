@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // 列表加载异常统一兜底为 Error 态（同 HomeViewModel 先例）；异常不再外传

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 仓库内「议题 / 拉取请求」分区的一行（投影模型：只带列表要展示的字段）。 */
data class RepoListItem(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val isPullRequest: Boolean,
    val isMerged: Boolean,
    val commentCount: Int,
    val updatedAt: String?,
    val labels: List<Pair<String, String?>>,
)

/** 仓库内议题/拉取请求列表状态（两个分区共用一次拉取的结果）。 */
data class RepoIssueListUiState(
    val items: List<RepoListItem> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val loaded: Boolean = false,
    val hasMore: Boolean = false,
) {
    val issues: List<RepoListItem> get() = items.filterNot { it.isPullRequest }
    val pulls: List<RepoListItem> get() = items.filter { it.isPullRequest }
}

/**
 * 仓库内「议题 / 拉取请求」分区的数据源（2026-10 UI 重构：仓库页新增两个分区页）。
 *
 * 复用 `GET /repos/{owner}/{repo}/issues`：该端点**同时返回 Issue 与 PR**（PR 条目带
 * `pull_request` 字段），一次请求喂两个分区，省一半配额（GitHub 的 REST 限制同样适用搜索外的
 * 常规端点，但同一份数据没有理由拉两次）。分页按 `page` 递增，末页判定 = 返回条数 < 每页上限。
 */
@HiltViewModel
class RepoIssueListViewModel
    @Inject
    constructor(
        private val issueApi: IssueApi,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RepoIssueListUiState())
        val uiState: StateFlow<RepoIssueListUiState> = _uiState.asStateFlow()

        private var owner: String = ""
        private var repo: String = ""
        private var page: Int = 1

        /** 首次进入任一分区时拉第一页（幂等：来回切分区不重复请求）。 */
        fun ensureLoaded(
            owner: String,
            repo: String,
        ) {
            if (_uiState.value.loaded || _uiState.value.loading) return
            this.owner = owner
            this.repo = repo
            load(reset = true)
        }

        /** 错误重试 */
        fun retry() {
            if (owner.isBlank() || repo.isBlank()) return
            load(reset = true)
        }

        /** 触底加载下一页 */
        fun loadMore() {
            val state = _uiState.value
            if (state.loading || !state.hasMore) return
            load(reset = false)
        }

        private fun load(reset: Boolean) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(loading = true, error = false)
                try {
                    if (reset) page = 1
                    val fetched =
                        issueApi
                            .listIssues(
                                owner = owner,
                                repo = repo,
                                state = "open",
                                page = page,
                                perPage = PAGE_SIZE,
                            ).map { it.toRepoListItem() }
                    val merged = if (reset) fetched else _uiState.value.items + fetched
                    _uiState.value =
                        RepoIssueListUiState(
                            items = merged,
                            loading = false,
                            error = false,
                            loaded = true,
                            hasMore = fetched.size >= PAGE_SIZE,
                        )
                    if (fetched.size >= PAGE_SIZE) page += 1
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(loading = false, error = true, loaded = true)
                }
            }
        }

        private fun IssueDto.toRepoListItem(): RepoListItem =
            RepoListItem(
                id = id,
                number = number,
                title = title,
                state = state,
                isPullRequest = pullRequest != null,
                isMerged = false,
                commentCount = comments,
                updatedAt = updatedAt,
                labels = labels.map { it.name to it.color },
            )

        private companion object {
            const val PAGE_SIZE = 30
        }
    }
