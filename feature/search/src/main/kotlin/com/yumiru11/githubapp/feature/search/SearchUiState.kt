package com.yumiru11.githubapp.feature.search

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import kotlinx.coroutines.flow.Flow

/**
 * 搜索结果 Tab（docs/ui-design.md §3.3：仓库/用户/Issue/PR + 代码搜索）。
 */
enum class SearchTab {
    REPOSITORIES,
    USERS,
    ISSUES,
    PULL_REQUESTS,
    CODE,
}

/**
 * 搜索页 UI 状态。
 *
 * - [Idle]：查询为空 → 展示搜索历史 + qualifier 建议
 * - [Loading]：新查询提交后加载中
 * - [Success]：内嵌当前 Tab 的 Paging 数据流（只构建活动 Tab 的流，
 *   搜索 API 限流严格，避免多路并行请求；分页错误由 UI 层
 *   LazyPagingItems.loadState 呈现与重试）
 * - [Error]：数据流构造期异常（正常路径由 PagingSource 消化）
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Success(
        val query: String,
        val activeTab: SearchTab,
        val repositories: Flow<PagingData<Repository>>,
        val users: Flow<PagingData<User>>,
        val issues: Flow<PagingData<SearchIssue>>,
        val pullRequests: Flow<PagingData<SearchIssue>>,
        val code: Flow<PagingData<SearchCodeItem>>,
    ) : SearchUiState

    data class Error(
        val errorType: SearchErrorType,
    ) : SearchUiState
}

/**
 * 搜索错误类型（UI 层 stringResource 映射文案，ViewModel/UI 只产类型）。
 */
enum class SearchErrorType {
    /** 网络/IO 错误 */
    NETWORK,

    /** 限流（429 / 403+Retry-After）——搜索 API 限流严格，需专门友好提示 */
    RATE_LIMITED,

    /** 凭据无效/过期（401/403） */
    UNAUTHORIZED,

    /** 其他未知错误 */
    UNKNOWN,
}

/**
 * 任意异常 → [SearchErrorType]（归一化 GitHubError 后再分类）。
 *
 * 覆盖 PagingSource 包装的 [GitHubRequestException] 与 ViewModel 数据流
 * 构造期的裸异常（HttpException/IOException）。
 */
fun Throwable.toSearchErrorType(): SearchErrorType =
    when (val error = (this as? GitHubRequestException)?.error ?: asGitHubError()) {
        is GitHubError.RateLimited -> SearchErrorType.RATE_LIMITED
        GitHubError.Unauthorized -> SearchErrorType.UNAUTHORIZED
        is GitHubError.Network -> SearchErrorType.NETWORK
        else -> SearchErrorType.UNKNOWN
    }
