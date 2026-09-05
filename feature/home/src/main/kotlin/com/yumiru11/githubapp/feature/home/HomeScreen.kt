package com.yumiru11.githubapp.feature.home

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.component.LocalHazeState
import com.yumiru11.githubapp.core.designsystem.component.LongBarAction
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppTopBar
import com.yumiru11.githubapp.feature.home.model.FeedItem
import com.yumiru11.githubapp.feature.home.ui.FeedRow
import com.yumiru11.githubapp.feature.home.ui.RepoPickerSheet
import com.yumiru11.githubapp.feature.home.ui.STAGGER_MAX_ITEMS
import com.yumiru11.githubapp.feature.home.ui.rememberStaggerEnterModifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** 快捷入口进列表后的稳定 item key（与 feed 条目的数字 id 命名空间不冲突） */
internal const val QUICK_ACTIONS_KEY = "home-quick-actions"

/**
 * 首页（T10 + #89 + #83）：玻璃头（顶栏 + 小分区条）+ HorizontalPager 分区内容区。
 *
 * - 登录态驱动：未登录 → 登录引导（T10 验收第 1 条）
 * - #89：分区改 [HorizontalPager] 跟手滑动；点 Tab 弹簧微回弹滚页（ui-design.md §2.1/§4.2 H1-1），
 *   拖页时 TabRow 指示条经 targetPage 即时跟随
 * - #89：动态页头部 LongBarAction ×3（新建 Issue / 查看 Pull Requests / 新建仓库占位禁用）。
 *   前两者经仓库选择器（[RepoPickerSheet]）取得 {owner}/{repo} 后由调用方路由到 T14/T15 页面
 * - 列表：Paging 分页（T10）+ PullToRefreshBox 下拉刷新（只作用于动态分区，B1-4）
 *   + 首屏 stagger 进入动效（[rememberStaggerEnterModifier]，经 MotionScale 缩放）
 * - 点击条目 → GitHubLinkParser 解析 html_url → 应用内导航（T10 验收第 4 条）
 * - 空/错/加载态齐全（T10 验收第 5 条）；分页加载错误由 LazyPagingItems.loadState 呈现
 *
 * ## backdrop blur 几何（issue #83：内容必须物理进入玻璃矩形）
 *
 * Haze 的 `hazeEffect` 只对**与玻璃矩形相交**的内容采样，所以本屏的布局约束是几何的：
 *
 * 1. **小分区条进玻璃头**（[AppTopBar] 的 `sectionBar` 副行插槽），而不是留在内容列里。
 *    留在内容列会把列表视口整体下推两行高度，feed 行永远滚不进顶栏矩形——
 *    玻璃每帧采样空背景，就是 FEEDBACK #17「毛玻璃只糊栏自身、看不出效果」的根因。
 * 2. **内容 Box 不再吃 Scaffold 的 top inset**（full-bleed），改为把玻璃头总高
 *    （含状态栏 + 顶栏 + 副行）推进滚动容器的 `contentPadding`：
 *    静止态首屏仍在玻璃下方，滚动时条目物理穿过玻璃背后 → 模糊 + 可辨「有东西在动」。
 * 3. **快捷入口（长条按钮）随 feed 一起滚动**（列表首项），否则它会把列表视口顶边
 *    压到 y=0 之下，第 2 条失效。非列表态（加载/空/错）没有可滚内容，快捷入口保持
 *    常驻在原位，视觉与改前一致。
 * 4. 顶栏与分区条共用**同一块** [com.yumiru11.githubapp.core.designsystem.component.GlassSurface]
 *    → 同一个 hazeEffect 矩形，玻璃层数不增（§6.2「≤2 层」上限由此守住）。
 * 5. 下拉刷新指示器按 [topGlassPadding] 下移，否则列表视口顶到 y=0 后指示器会藏在玻璃头背后。
 *
 * 内容侧 `hazeSource` 挂在本屏自持的 [LocalHazeState] 上（覆盖 MainTabPager 提供的
 * 底栏 state），避免顶栏 effect 嵌套进底栏 source 子树。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    blurEnabled: Boolean = true,
    /** 底栏玻璃总高（MainTabPager 传入）：作为列表 contentPadding，让内容滚进玻璃背后 */
    bottomContentPadding: Dp = 0.dp,
    onLoginClick: () -> Unit = {},
    onFeedItemClick: (ParsedUrl) -> Unit = {},
    /** #89：仓库选择器选中后路由到 issue_create/{owner}/{repo} */
    onCreateIssue: (owner: String, repo: String) -> Unit = { _, _ -> },
    /** #89：仓库选择器选中后路由到 pulls/{owner}/{repo} */
    onViewPullRequests: (owner: String, repo: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // backdrop blur（issue #83）：顶栏玻璃的 hazeEffect 与本页内容侧 hazeSource 共享本 state
    val hazeState = rememberHazeState()
    // source 侧门禁与 GlassSurface 的 effect 侧同源判定（防两侧漂移）
    val useHazeSource = GlassRenderPolicy.shouldAttachHazeSource(blurEnabled)

    // pager 状态上提到这里：分区条已进玻璃头（顶栏插槽），两者要读写同一份状态
    val pagerState = rememberPagerState { HomeTab.entries.size }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = modifier,
            // 内容 full-bleed（insets 由本组件手工分配到滚动容器 contentPadding）
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                HomeGlassHeader(
                    pagerState = pagerState,
                    blurEnabled = blurEnabled,
                    onSearchClick = onSearchClick,
                    onNotificationClick = onNotificationClick,
                    onProfileClick = onProfileClick,
                )
            },
        ) { paddingValues ->
            // 玻璃头总高（状态栏 + 顶栏 + 副行分区条）：内容视口铺到 y=0，由此高度避让
            val topGlassPadding = paddingValues.calculateTopPadding()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (useHazeSource) {
                                // 顶栏玻璃的模糊源：feed 列表等内容（含玻璃头背后的区域）
                                Modifier.hazeSource(hazeState)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        LoadingContent(
                            modifier = Modifier.fillMaxSize().padding(top = topGlassPadding),
                        )
                    }

                    is HomeUiState.Unauthenticated -> {
                        UnauthenticatedContent(
                            onLoginClick = onLoginClick,
                            modifier = Modifier.fillMaxSize().padding(top = topGlassPadding),
                        )
                    }

                    is HomeUiState.Error -> {
                        ErrorContent(
                            errorType = state.errorType,
                            onRetry = { viewModel.retry() },
                            modifier = Modifier.fillMaxSize().padding(top = topGlassPadding),
                        )
                    }

                    is HomeUiState.Success -> {
                        HomeSuccessContent(
                            feed = state.feed,
                            pagerState = pagerState,
                            onFeedItemClick = onFeedItemClick,
                            topGlassPadding = topGlassPadding,
                            bottomContentPadding = bottomContentPadding,
                            onCreateIssue = onCreateIssue,
                            onViewPullRequests = onViewPullRequests,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 首页玻璃头（issue #83）：顶栏 + 小分区条，同处一块 [GlassSurface] 矩形。
 *
 * - `zIndex` 让玻璃头在内容（hazeSource）之后绘制——Haze 要求 source 先画
 * - 分区条走 [AppTopBar] 的 `sectionBar` 副行插槽，因此它**在玻璃矩形之内**：
 *   Scaffold 交给内容区的 top inset 自动含两行高度，feed 视口才铺得到玻璃背后
 * - 点 Tab 弹簧微回弹滚页（#89 H1-1，令牌走 [AppMotion]）
 */
@Composable
private fun HomeGlassHeader(
    pagerState: PagerState,
    blurEnabled: Boolean,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.zIndex(1f)) {
        AppTopBar(
            onSearchClick = onSearchClick,
            onNotificationClick = onNotificationClick,
            onProfileClick = onProfileClick,
            blurEnabled = blurEnabled,
            sectionBar = {
                HomeTabBar(
                    selectedTabIndex = pagerState.targetPage.coerceIn(0, HomeTab.entries.lastIndex),
                    onTabSelected = { tab ->
                        if (tab.ordinal != pagerState.targetPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = tab.ordinal,
                                    animationSpec =
                                        spring(
                                            dampingRatio = AppMotion.DampingRatioHighBouncy,
                                            stiffness = AppMotion.StiffnessMedium,
                                        ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

/**
 * 登录成功后的分区内容（#89）：Pager 分页 + 仓库选择器宿主。
 *
 * 小分区条已上移到玻璃头副行（issue #83），这里只剩 pager 本体。
 */
@Composable
private fun HomeSuccessContent(
    feed: Flow<PagingData<FeedItem>>,
    pagerState: PagerState,
    onFeedItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onCreateIssue: (owner: String, repo: String) -> Unit,
    onViewPullRequests: (owner: String, repo: String) -> Unit,
) {
    val pickerViewModel: RepoPickerViewModel = hiltViewModel()
    val pickerUiState by pickerViewModel.uiState.collectAsStateWithLifecycle()
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    var pickerTarget by rememberSaveable { mutableStateOf(PickerTarget.CREATE_ISSUE) }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        HomePage(
            page = page,
            feed = feed,
            onFeedItemClick = onFeedItemClick,
            topGlassPadding = topGlassPadding,
            bottomContentPadding = bottomContentPadding,
            onCreateIssueClick = {
                pickerTarget = PickerTarget.CREATE_ISSUE
                pickerVisible = true
            },
            onViewPullRequestsClick = {
                pickerTarget = PickerTarget.VIEW_PULL_REQUESTS
                pickerVisible = true
            },
        )
    }
    RepoPickerSheet(
        visible = pickerVisible,
        uiState = pickerUiState,
        onPick = { owner, repo ->
            pickerVisible = false
            if (pickerTarget == PickerTarget.CREATE_ISSUE) {
                onCreateIssue(owner, repo)
            } else {
                onViewPullRequests(owner, repo)
            }
        },
        onDismiss = { pickerVisible = false },
        onRetry = { pickerViewModel.retry() },
    )
}

/** Pager 单页分发（动态页含快捷入口；Issue/PR 列表为占位空态）。 */
@Composable
private fun HomePage(
    page: Int,
    feed: Flow<PagingData<FeedItem>>,
    onFeedItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onCreateIssueClick: () -> Unit,
    onViewPullRequestsClick: () -> Unit,
) {
    when (HomeTab.entries[page]) {
        HomeTab.FEED -> {
            FeedPage(
                feed = feed,
                onFeedItemClick = onFeedItemClick,
                topGlassPadding = topGlassPadding,
                bottomContentPadding = bottomContentPadding,
                onCreateIssueClick = onCreateIssueClick,
                onViewPullRequestsClick = onViewPullRequestsClick,
                modifier = Modifier.fillMaxSize(),
            )
        }

        HomeTab.ISSUES -> {
            // Issue 列表占位（列表页属后续功能票；详情页 T14 已合入）
            EmptyContent(modifier = Modifier.fillMaxSize().padding(top = topGlassPadding))
        }

        HomeTab.PULL_REQUESTS -> {
            // PR 列表占位（列表页属后续功能票；详情页 T15 已合入）
            EmptyContent(modifier = Modifier.fillMaxSize().padding(top = topGlassPadding))
        }
    }
}

/** 首页快捷入口区（#89，ui-design.md §2.2）：Home 字样 + 长条按钮 ×3。 */
@Composable
private fun QuickActionsSection(
    onCreateIssueClick: () -> Unit,
    onViewPullRequestsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comingSoon = stringResource(R.string.home_action_create_repo_cd)
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        LongBarAction(
            text = stringResource(R.string.home_action_create_issue),
            icon = AppDevOcticons.IssueOpened,
            onClick = onCreateIssueClick,
        )
        LongBarAction(
            text = stringResource(R.string.home_action_view_pulls),
            icon = AppDevOcticons.PullRequest,
            onClick = onViewPullRequestsClick,
        )
        // 新建仓库：占位待功能票（issue #89 任务清单），禁用态语义可辨
        LongBarAction(
            text = stringResource(R.string.home_action_create_repo),
            icon = AppDevOcticons.Repo,
            onClick = {},
            enabled = false,
            modifier =
                Modifier.semantics {
                    stateDescription = comingSoon
                },
        )
    }
}

/**
 * 动态页（#89）：快捷入口区 + feed 状态区；下拉刷新只作用于本分区（B1-4 只刷新当前分区）。
 *
 * issue #83 几何：**有 feed 条目时**快捷入口进列表首项（随内容滚走），列表视口顶到 y=0，
 * 条目才会物理穿过玻璃背后；**无条目时**（加载/空/错）没有可滚内容，快捷入口保持常驻。
 */
@Composable
private fun FeedPage(
    feed: Flow<PagingData<FeedItem>>,
    onFeedItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onCreateIssueClick: () -> Unit,
    onViewPullRequestsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = feed.collectAsLazyPagingItems()
    val refreshState = lazyItems.loadState.refresh
    val quickActions: @Composable () -> Unit = {
        QuickActionsSection(
            onCreateIssueClick = onCreateIssueClick,
            onViewPullRequestsClick = onViewPullRequestsClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val hasFeedRows = refreshState !is LoadState.Error && lazyItems.itemCount > 0

    if (hasFeedRows) {
        FeedList(
            lazyItems = lazyItems,
            onFeedItemClick = onFeedItemClick,
            topGlassPadding = topGlassPadding,
            bottomContentPadding = bottomContentPadding,
            quickActions = quickActions,
            modifier = modifier,
        )
    } else {
        // 无条目（加载 / 空 / 错）：没有可滚内容，快捷入口保持常驻，
        // 整块按玻璃头高度整体避让（此处用布局 padding 是安全的——不参与穿越）
        Column(modifier = modifier.padding(top = topGlassPadding)) {
            // 列表态横向 16dp 由 contentPadding 给；这里没有列表，横向自己补
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                quickActions()
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    refreshState is LoadState.Error -> {
                        PagingErrorContent(
                            error = (refreshState as LoadState.Error).error,
                            onRetry = { lazyItems.retry() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    refreshState is LoadState.Loading -> {
                        LoadingContent(modifier = Modifier.fillMaxSize())
                    }

                    else -> {
                        // 空态保留下拉刷新（T10 B1-4）
                        PullToRefreshBox(
                            isRefreshing = refreshState is LoadState.Loading,
                            onRefresh = { lazyItems.refresh() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            EmptyContent(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/**
 * 动态列表：full-bleed 视口 + 快捷入口首项 + 首屏 stagger（#89）。
 *
 * issue #83：`contentPadding` 顶部给玻璃头高度（静止态首屏在玻璃下、滚动穿过玻璃），
 * 底部追加底栏玻璃高度（末条可滚出玻璃区）；下拉刷新指示器同步下移避让玻璃头。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    lazyItems: LazyPagingItems<FeedItem>,
    onFeedItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    quickActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    val isRefreshing = lazyItems.loadState.refresh is LoadState.Loading
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { lazyItems.refresh() },
        modifier = modifier,
        state = pullState,
        indicator = {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = topGlassPadding)) {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topGlassPadding,
                    bottom = 8.dp + bottomContentPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = QUICK_ACTIONS_KEY) {
                quickActions()
            }
            items(
                count = lazyItems.itemCount,
                // itemKey 内部用 peek(index)（不触发页加载，未加载区回退占位 key），
                // 稳定 id 键保证翻页/刷新时已有行不重组合、滚动位置不跳变。
                // ⚠️ itemKey/key 逻辑归 #86（perf(list)）领地，本票不改其行为。
                key = lazyItems.itemKey { it.id },
            ) { index ->
                val item = lazyItems[index] ?: return@items
                val enterModifier =
                    if (index < STAGGER_MAX_ITEMS) {
                        rememberStaggerEnterModifier(index)
                    } else {
                        Modifier
                    }
                Box(modifier = enterModifier) {
                    FeedRow(
                        item = item,
                        onClick = { handleItemClick(item, onFeedItemClick) },
                    )
                }
            }
        }
    }
}

/** 点击条目：解析 html_url → 应用内 ParsedUrl 导航（External 防御性忽略） */
private fun handleItemClick(
    item: FeedItem,
    onFeedItemClick: (ParsedUrl) -> Unit,
) {
    val htmlUrl = item.htmlUrl ?: return
    val parsed = GitHubLinkParser.parseUrl(htmlUrl)
    if (parsed !is ParsedUrl.External) {
        onFeedItemClick(parsed)
    }
}

@Composable
private fun UnauthenticatedContent(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.feed_require_login),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLoginClick) {
                Text(text = stringResource(R.string.feed_login))
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    // #84：共享加载态组件
    AppLoadingState(modifier = modifier)
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    // #84：共享空态组件（矢量插图 + 文案）
    AppEmptyState(
        icon = AppDevOcticons.Repo,
        title = stringResource(R.string.feed_empty),
        modifier = modifier,
    )
}

@Composable
private fun PagingErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorType = if (error is IOException || error is HttpException) HomeErrorType.NETWORK else HomeErrorType.UNKNOWN
    ErrorContent(errorType = errorType, onRetry = onRetry, modifier = modifier)
}

@Composable
private fun ErrorContent(
    errorType: HomeErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // #84：共享错误态组件（Alert 插图 + 文案 + 重试按钮）
    AppErrorState(
        title = errorMessage(errorType),
        actionLabel = stringResource(R.string.feed_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** 错误类型 → 本地化文案（ViewModel 只传类型，不产英文） */
@Composable
private fun errorMessage(errorType: HomeErrorType): String =
    when (errorType) {
        HomeErrorType.NETWORK -> stringResource(R.string.feed_error_network)
        HomeErrorType.UNKNOWN -> stringResource(R.string.feed_error_unknown)
    }

/**
 * 首页顶部小分区条（动态/Issue/PR）—— ui-design.md §2.1；#89 起与 Pager 双向联动。
 *
 * issue #83：由 [AppTopBar] 的 `sectionBar` 插槽渲染在**玻璃头副行**内（不再是内容列首项），
 * containerColor 保持透明让背后模糊透得上来；文字/指示条在 effect 层，自身保持锐利。
 */
@Composable
private fun HomeTabBar(
    selectedTabIndex: Int,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
    ) {
        HomeTab.entries.forEach { tab ->
            Tab(
                selected = selectedTabIndex == tab.ordinal,
                onClick = { onTabSelected(tab) },
                text = { Text(text = stringResource(tab.titleRes)) },
            )
        }
    }
}
