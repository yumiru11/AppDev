@file:Suppress("TooManyFunctions", "LongMethod")
// - TooManyFunctions：结果区/历史/qualifier/错误态各是一个小 Composable，Compose 惯用结构，
//   拆文件反损可读性（RepoDetailScreen/BranchesScreen 同款先例；#167 / UI16 新增顶部细进度条后越阈值）
// - LongMethod：SearchScreen 是入口装配（搜索框回调 + 顶部进度条 + 四态内容分发），
//   分支聚合在一处才好对照（IssueDetailScreen/PullRequestDetailScreen 同款先例）
@file:OptIn(ExperimentalLayoutApi::class)

package com.yumiru11.githubapp.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.appFadeThroughTransform
import com.yumiru11.githubapp.feature.search.qualifier.QUALIFIER_SUGGESTIONS
import com.yumiru11.githubapp.feature.search.qualifier.appendQualifier
import kotlinx.coroutines.flow.Flow

/** 顶部细进度条测试标记（「搜索中不整区闪 loading」断言用）。 */
internal const val SEARCH_PROGRESS_TAG = "search-progress"

/**
 * 搜索页（T18，docs/ui-design.md §3.3）。
 *
 * - 大号搜索框（自动聚焦）+ 历史记录 chips（可清空）+ qualifier 快速建议
 * - 结果 Tabs（仓库/用户/Issue/PR/代码），每 Tab 独立 Paging
 * - 代码搜索需登录：未登录展示登录引导（T18 验收第 4 条）
 * - 限流（429）与网络错误：分页错误按 GitHubError 分类展示友好文案（验收第 5 条）
 * - 点击结果 → GitHubLinkParser 解析 html_url → 应用内路由（回调由宿主接线）
 * - **结果区动效（#167 / UI16）**：换关键词 / 切 Tab 走 M3 fade-through（[appFadeThroughTransform]，
 *   动画键 = 查询词 + 选中 Tab）；搜索中只在**顶部**显示细 [LinearProgressIndicator]，
 *   结果区保留上一份成功结果——不再整区闪 loading
 */
@Composable
fun SearchScreen(
    onBackClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onResultClick: (ParsedUrl) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val rateLimitWarning by viewModel.rateLimitWarning.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    // #167 / UI16：Loading 期间保留上一份成功结果（记在本地而非 VM——纯展示态，
    // 配置变更后重来一次也无妨），配合顶部细进度条，避免整区闪 loading。
    var lastResults by remember { mutableStateOf<SearchUiState.Success?>(null) }
    LaunchedEffect(uiState) {
        (uiState as? SearchUiState.Success)?.let { lastResults = it }
    }
    // 活动 Tab 的 Paging 首屏刷新（真正的网络等待期）也计入"搜索中"
    var resultsRefreshing by remember { mutableStateOf(false) }
    val results = (uiState as? SearchUiState.Success) ?: lastResults
    val isSearching = uiState is SearchUiState.Loading || resultsRefreshing

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchTopBar(
                input = input,
                onInputChange = viewModel::onQueryChange,
                onSubmit = {
                    viewModel.submitQuery(input)
                    keyboard?.hide()
                },
                onBackClick = onBackClick,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // 搜索中的唯一进度反馈（细条，不挤占结果区）
            SearchProgressBar(visible = isSearching)
            Box(modifier = Modifier.fillMaxSize()) {
                val state = uiState
                when {
                    state is SearchUiState.Idle -> {
                        IdleContent(
                            history = history,
                            input = input,
                            onHistoryClick = { viewModel.submitQuery(it) },
                            onClearHistory = viewModel::clearHistory,
                            onQualifierClick = { qualifier ->
                                val next = appendQualifier(input, qualifier)
                                viewModel.onQueryChange(next)
                                viewModel.submitQuery(next)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state is SearchUiState.Error -> {
                        ErrorContent(
                            errorType = state.errorType,
                            onRetry = viewModel::retry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Loading（有历史结果）与 Success 走同一分支：加载中不换掉内容
                    results != null -> {
                        // 结果区整块离场（清空输入 / 转错误态）时清掉刷新标记，
                        // 否则顶部进度条会一直亮着（子内容已卸载，没人再上报）
                        DisposableEffect(Unit) { onDispose { resultsRefreshing = false } }
                        ResultsContent(
                            results = results,
                            isLoggedIn = isLoggedIn,
                            rateLimitWarning = rateLimitWarning,
                            onTabSelected = viewModel::selectTab,
                            onLoginClick = onLoginClick,
                            onResultClick = onResultClick,
                            onRefreshingChange = { resultsRefreshing = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 首次搜索：没有可保留的结果，保留居中占位（顶部仍有细进度条）
                    else -> {
                        LoadingContent(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * 顶部细进度条（#167 / UI16）：搜索中在结果区**顶部**显示不确定进度条，
 * 出现/消失的淡入淡出走 fade-through 出场时长令牌（折算为 0 即直接显隐）。
 */
@Composable
private fun SearchProgressBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.search_in_progress)
    val fadeMillis = AppMotion.scaledDuration(AppMotion.DURATION_FADE_THROUGH_OUT)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(animationSpec = tween(fadeMillis, easing = AppMotion.EmphasizedDecelerate)),
        exit = fadeOut(animationSpec = tween(fadeMillis, easing = AppMotion.EmphasizedAccelerate)),
    ) {
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SEARCH_PROGRESS_TAG)
                    // 读屏播报"正在搜索"（M3 进度条自带 progress 语义，这里补可读文案）
                    .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun ResultsContent(
    results: SearchUiState.Success,
    isLoggedIn: Boolean,
    rateLimitWarning: SearchRateLimitWarning?,
    onTabSelected: (SearchTab) -> Unit,
    onLoginClick: () -> Unit,
    onResultClick: (ParsedUrl) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 限流提示条（plan.md §9.3）：剩余配额偏低时置顶于结果区
        if (rateLimitWarning != null) {
            SearchRateLimitSection(warning = rateLimitWarning)
        }
        ResultTabs(
            selectedTab = results.activeTab,
            onTabSelected = onTabSelected,
        )
        // 结果区 M3 fade-through（#167 / UI16，ui-design §3.3「输入防抖 300ms 后结果区 Crossfade」
        // + §4.3 渐入渐出）：换关键词与切 Tab 都是"同一块区域换内容"——
        // 旧内容先淡出，新内容延迟同样的时长后淡入 + 自 92% 放大。
        // 动画键 = 查询词 + 选中 Tab（contentKey）：只有这两者变化才播动效，
        // 限流提示、登录态等旁路更新不触发闪烁；时长经 AppMotion 折算（0 = 直接切换）。
        val fadeInMillis = AppMotion.scaledDuration(AppMotion.DURATION_FADE_THROUGH_IN)
        val fadeOutMillis = AppMotion.scaledDuration(AppMotion.DURATION_FADE_THROUGH_OUT)
        AnimatedContent(
            targetState = results,
            transitionSpec = { appFadeThroughTransform(fadeInMillis, fadeOutMillis) },
            contentKey = { it.query to it.activeTab },
            label = "search-results",
        ) { resultsState ->
            when (resultsState.activeTab) {
                SearchTab.REPOSITORIES -> {
                    RepositoriesContent(
                        flow = resultsState.repositories,
                        onResultClick = onResultClick,
                        onRefreshingChange = onRefreshingChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SearchTab.USERS -> {
                    UsersContent(
                        flow = resultsState.users,
                        onResultClick = onResultClick,
                        onRefreshingChange = onRefreshingChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SearchTab.ISSUES -> {
                    IssuesContent(
                        flow = resultsState.issues,
                        onResultClick = onResultClick,
                        onRefreshingChange = onRefreshingChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SearchTab.PULL_REQUESTS -> {
                    IssuesContent(
                        flow = resultsState.pullRequests,
                        onResultClick = onResultClick,
                        onRefreshingChange = onRefreshingChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SearchTab.CODE -> {
                    if (isLoggedIn) {
                        CodeContent(
                            flow = resultsState.code,
                            onResultClick = onResultClick,
                            onRefreshingChange = onRefreshingChange,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CodeLoginGateContent(
                            onLoginClick = onLoginClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    history: List<String>,
    input: String,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onQualifierClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (history.isNotEmpty()) {
            item {
                HistorySection(
                    history = history,
                    onHistoryClick = onHistoryClick,
                    onClearHistory = onClearHistory,
                )
            }
        }
        if (input.isEmpty()) {
            item {
                QualifierSection(onQualifierClick = onQualifierClick)
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearHistory) {
                Text(text = stringResource(R.string.search_history_clear))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            history.forEach { query ->
                SuggestionChip(
                    onClick = { onHistoryClick(query) },
                    label = {
                        Text(
                            text = query,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun QualifierSection(
    onQualifierClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(R.string.search_qualifier_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUALIFIER_SUGGESTIONS.forEach { qualifier ->
                SuggestionChip(
                    onClick = { onQualifierClick(qualifier.value) },
                    label = {
                        Text(
                            text = qualifier.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultTabs(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 8.dp,
    ) {
        SearchTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tabLabel(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun tabLabel(tab: SearchTab): String =
    when (tab) {
        SearchTab.REPOSITORIES -> stringResource(R.string.search_tab_repositories)
        SearchTab.USERS -> stringResource(R.string.search_tab_users)
        SearchTab.ISSUES -> stringResource(R.string.search_tab_issues)
        SearchTab.PULL_REQUESTS -> stringResource(R.string.search_tab_pull_requests)
        SearchTab.CODE -> stringResource(R.string.search_tab_code)
    }

@Composable
private fun RepositoriesContent(
    flow: Flow<PagingData<Repository>>,
    onResultClick: (ParsedUrl) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        keyOf = { it.fullName },
        modifier = modifier,
        onRefreshingChange = onRefreshingChange,
        row = { repository ->
            RepositoryRow(
                repository = repository,
                onClick = {
                    val url = "https://github.com/${repository.ownerLogin}/${repository.name}"
                    handleResultClick(url, onResultClick)
                },
            )
        },
    )
}

@Composable
private fun UsersContent(
    flow: Flow<PagingData<User>>,
    onResultClick: (ParsedUrl) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        keyOf = { it.login },
        modifier = modifier,
        onRefreshingChange = onRefreshingChange,
        row = { user ->
            UserRow(
                user = user,
                onClick = { user.url?.let { handleResultClick(it, onResultClick) } },
            )
        },
    )
}

@Composable
private fun IssuesContent(
    flow: Flow<PagingData<SearchIssue>>,
    onResultClick: (ParsedUrl) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        keyOf = { it.id },
        modifier = modifier,
        onRefreshingChange = onRefreshingChange,
        row = { issue ->
            IssueRow(
                issue = issue,
                onClick = { issue.htmlUrl?.let { handleResultClick(it, onResultClick) } },
            )
        },
    )
}

@Composable
private fun CodeContent(
    flow: Flow<PagingData<SearchCodeItem>>,
    onResultClick: (ParsedUrl) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        // 代码搜索结果无唯一 id，用「仓库 + 文件路径」组合键（同一文件在结果集中至多一条）
        keyOf = { "${it.repoFullName}/${it.path}" },
        modifier = modifier,
        onRefreshingChange = onRefreshingChange,
        row = { item ->
            CodeRow(
                item = item,
                onClick = { item.htmlUrl?.let { handleResultClick(it, onResultClick) } },
            )
        },
    )
}

/**
 * 通用分页结果列表：刷新错误 → 全屏错误（按错误类型本地化文案）；
 * 追加错误 → 底部重试行；空结果 → 空态。
 *
 * #167 / UI16：首屏刷新**不再**在结果区闪 loading（[LoadingContent]）——
 * 进度反馈统一由顶部细进度条承担（[onRefreshingChange] 把它上抛），
 * 这里留空以避免"无结果"空态在加载期误闪。
 */
@Composable
private fun <T : Any> SearchPagingList(
    lazyItems: LazyPagingItems<T>,
    keyOf: (T) -> Any,
    row: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    onRefreshingChange: (Boolean) -> Unit = {},
) {
    // 只有"首屏还没有任何行"的刷新才算搜索中（顶部细进度条）；已有内容的缓存命中不闪进度条
    val refreshing = lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0
    LaunchedEffect(refreshing) { onRefreshingChange(refreshing) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val refreshError = lazyItems.loadState.refresh as? LoadState.Error
        when {
            refreshError != null && lazyItems.itemCount == 0 -> {
                item {
                    PagingErrorContent(
                        error = refreshError.error,
                        onRetry = lazyItems::retry,
                    )
                }
            }

            refreshing && lazyItems.itemCount == 0 -> {
                // 顶部细进度条已表达加载中，内容区留空（不闪空态、不闪 loading）
            }

            lazyItems.itemCount == 0 -> {
                item { EmptyContent() }
            }

            else -> {
                items(
                    count = lazyItems.itemCount,
                    // itemKey 内部用 peek(index)（不触发页加载，未加载区回退占位 key），
                    // 稳定 id 键保证翻页/刷新时已有行不重组合、滚动位置不跳变。
                    key = lazyItems.itemKey(keyOf),
                ) { index ->
                    val item = lazyItems[index] ?: return@items
                    row(item)
                }
                if (lazyItems.loadState.append is LoadState.Error) {
                    item { AppendErrorRow(onRetry = lazyItems::retry) }
                }
            }
        }
    }
}

@Composable
private fun CodeLoginGateContent(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.search_code_require_login),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLoginClick) {
                Text(text = stringResource(R.string.search_login))
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.search_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppendErrorRow(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.search_load_more_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.search_retry))
        }
    }
}

@Composable
private fun PagingErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorContent(errorType = error.toSearchErrorType(), onRetry = onRetry, modifier = modifier)
}

@Composable
private fun ErrorContent(
    errorType: SearchErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage(errorType),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.search_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel/UI 只产类型，不产英文） */
@Composable
private fun errorMessage(errorType: SearchErrorType): String =
    when (errorType) {
        SearchErrorType.NETWORK -> stringResource(R.string.search_error_network)
        SearchErrorType.RATE_LIMITED -> stringResource(R.string.search_error_rate_limited)
        SearchErrorType.UNAUTHORIZED -> stringResource(R.string.search_error_unauthorized)
        SearchErrorType.UNKNOWN -> stringResource(R.string.search_error_unknown)
    }
