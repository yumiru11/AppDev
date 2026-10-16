@file:Suppress("LongMethod", "LongParameterList") // 屏幕装配（玻璃头/五分区 Pager/FAB/各分区内容）结构固有

package com.yumiru11.githubapp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.yumiru11.githubapp.core.designsystem.component.AppCard
import com.yumiru11.githubapp.core.designsystem.component.AppChevron
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppFabMenu
import com.yumiru11.githubapp.core.designsystem.component.AppFabMenuItem
import com.yumiru11.githubapp.core.designsystem.component.AppFilterChipRow
import com.yumiru11.githubapp.core.designsystem.component.AppGroupedColumn
import com.yumiru11.githubapp.core.designsystem.component.AppIconBadge
import com.yumiru11.githubapp.core.designsystem.component.AppListRow
import com.yumiru11.githubapp.core.designsystem.component.AppListSkeleton
import com.yumiru11.githubapp.core.designsystem.component.AppScaffold
import com.yumiru11.githubapp.core.designsystem.component.AppScrollableTabs
import com.yumiru11.githubapp.core.designsystem.component.AppSectionHeader
import com.yumiru11.githubapp.core.designsystem.component.AppStatPill
import com.yumiru11.githubapp.core.designsystem.component.AppTab
import com.yumiru11.githubapp.core.designsystem.component.LocalHazeState
import com.yumiru11.githubapp.core.designsystem.component.labelColors
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppTopBar
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.home.model.FeedItem
import com.yumiru11.githubapp.feature.home.model.HomeDiscussionItem
import com.yumiru11.githubapp.feature.home.model.HomeIssueFilter
import com.yumiru11.githubapp.feature.home.model.HomeIssueItem
import com.yumiru11.githubapp.feature.home.model.HomeLabel
import com.yumiru11.githubapp.feature.home.model.HomePullFilter
import com.yumiru11.githubapp.feature.home.model.HomeUserItem
import com.yumiru11.githubapp.feature.home.model.TrendItem
import com.yumiru11.githubapp.feature.home.ui.FeedRow
import com.yumiru11.githubapp.feature.home.ui.FeedSkeleton
import com.yumiru11.githubapp.feature.home.ui.RepoPickerSheet
import com.yumiru11.githubapp.feature.home.ui.TrendingSection
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** 发现分区的主题 chips（静态表，点击进入搜索）。 */
private val EXPLORE_TOPICS =
    listOf(
        "kotlin",
        "android",
        "compose",
        "machine-learning",
        "typescript",
        "rust",
        "golang",
        "python",
        "devops",
        "security",
        "web",
        "flutter",
    )

/**
 * 首页（2026-10 UI 重构：对齐 GitHubM3E 的 5 分区版式）。
 *
 * 结构：玻璃头（胶囊搜索 + 通知/账户 + 可滚动分区条）→ HorizontalPager 五页：
 * 动态 / 议题 / 拉取请求 / 讨论 / 发现。分区条与 Pager 双向联动（滑动跟手、点条滚页、
 * 选中项自动滚入视口）。
 *
 * 保持不变的契约（勿动）：
 * - 玻璃几何：分区条在玻璃矩形内（AppTopBar 的 sectionBar 副行），内容视口铺到 y=0，
 *   由 Scaffold 的 top padding 避让 —— 列表滚动穿过玻璃背后（issue #83/#89 真机修复）；
 * - 底部避让：bottomContentPadding（底栏玻璃总高）落到各列表 contentPadding；
 * - 分区首载幂等（搜索配额守卫）、游客引导、错误重试、仓库选择器接线。
 */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    /** 底栏玻璃总高（MainTabPager 传入）：作为列表 contentPadding，让内容滚进玻璃背后 */
    bottomContentPadding: Dp = 0.dp,
    onLoginClick: () -> Unit = {},
    onFeedItemClick: (ParsedUrl) -> Unit = {},
    /** #89：仓库选择器选中后路由到 issue_create/{owner}/{repo} */
    onCreateIssue: (owner: String, repo: String) -> Unit = { _, _ -> },
    /** #89：仓库选择器选中后路由到 pulls/{owner}/{repo} */
    onViewPullRequests: (owner: String, repo: String) -> Unit = { _, _ -> },
    /** L04：长条按钮「新建仓库」→ 新建仓库页（未登录由宿主先引导登录） */
    onCreateRepo: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    // 与 viewModel 同形参化：屏幕级测试可直接注入（默认 hiltViewModel() 保持生产接线不变）
    pickerViewModel: RepoPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val pulls by viewModel.pulls.collectAsStateWithLifecycle()
    val discussions by viewModel.discussions.collectAsStateWithLifecycle()
    val suggestedUsers by viewModel.suggestedUsers.collectAsStateWithLifecycle()
    val hotIssues by viewModel.hotIssues.collectAsStateWithLifecycle()

    // backdrop blur（issue #83）：顶栏玻璃的 hazeEffect 与本页内容侧 hazeSource 共享本 state
    val hazeState = rememberHazeState()
    // 玻璃开关按「顶栏」点位取（#167 / UI03）；source 侧门禁与 effect 侧同源判定
    val blurEnabled = LocalGlassSettings.current.enabledFor(GlassScope.TOP_BAR)
    val useHazeSource = GlassRenderPolicy.shouldAttachHazeSource(blurEnabled)

    val pagerState = rememberPagerState { HomeTab.entries.size }
    val scope = rememberCoroutineScope()

    // #167 / UI16：点 Tab 切分区时用于 fade-through 的信号（只有点击才自增）
    val tabClickTicks = remember { mutableIntStateOf(0) }

    // 分区首载（幂等）：滑动或点条到某页时触发一次（搜索配额守卫在 VM 内）
    LaunchedEffect(pagerState.targetPage) {
        HomeTab.entries.getOrNull(pagerState.targetPage)?.let(viewModel::ensureSectionStarted)
    }

    // FAB 菜单：滑动自动收起、停滑 300ms 后恢复（AppFabMenu 内置）
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var contentScrolling by remember { mutableStateOf(false) }

    // 仓库选择器目标与可见性（新建 Issue / 查看拉取请求）：与重构前同一契约
    var pickerTarget by rememberSaveable { mutableStateOf(PickerTarget.CREATE_ISSUE) }
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    val pickerUiState by pickerViewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        AppScaffold(
            modifier = modifier,
            // 内容 full-bleed（insets 由本组件手工分配到滚动容器 contentPadding）
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                HomeGlassHeader(
                    tabClickTicks = tabClickTicks.intValue,
                    pagerState = pagerState,
                    blurEnabled = blurEnabled,
                    onSearchClick = onSearchClick,
                    onNotificationClick = onNotificationClick,
                    onProfileClick = onProfileClick,
                    onTabClick = { tabClickTicks.intValue++ },
                    onSelectTab = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            },
            floatingActionButton = {
                AppFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    mainIcon = AppIcons.Add.rounded,
                    mainContentDescription = stringResource(R.string.home_fab_new_issue),
                    isScrollInProgress = contentScrolling,
                    items =
                        listOf(
                            AppFabMenuItem(
                                label = stringResource(R.string.home_action_view_pulls),
                                icon = AppIcons.MergeType.rounded,
                                onClick = {
                                    pickerTarget = PickerTarget.VIEW_PULL_REQUESTS
                                    pickerVisible = true
                                },
                            ),
                            AppFabMenuItem(
                                label = stringResource(R.string.home_fab_new_repo),
                                icon = AppIcons.CreateNewFolder.rounded,
                                onClick = onCreateRepo,
                            ),
                            AppFabMenuItem(
                                label = stringResource(R.string.home_fab_new_issue),
                                icon = AppIcons.BugReport.rounded,
                                onClick = {
                                    pickerTarget = PickerTarget.CREATE_ISSUE
                                    pickerVisible = true
                                },
                            ),
                        ),
                )
            },
        ) { paddingValues ->
            // 玻璃头总高（状态栏 + 顶栏 + 副行分区条）：内容视口铺到 y=0，由此高度避让
            val topGlassPadding = paddingValues.calculateTopPadding()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (useHazeSource) Modifier.hazeSource(hazeState) else Modifier),
            ) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        FeedSkeleton(modifier = Modifier.fillMaxSize().padding(top = topGlassPadding))
                    }

                    is HomeUiState.Unauthenticated -> {
                        AppEmptyState(
                            icon = AppDevOcticons.Eye,
                            title = stringResource(R.string.feed_require_login),
                            message = stringResource(R.string.feed_empty),
                            actionLabel = stringResource(R.string.feed_login),
                            onAction = onLoginClick,
                            modifier = Modifier.fillMaxSize().padding(top = topGlassPadding),
                        )
                    }

                    is HomeUiState.Error -> {
                        AppErrorState(
                            title = errorMessage(state.errorType),
                            actionLabel = stringResource(R.string.feed_retry),
                            onAction = { viewModel.retry() },
                            modifier = Modifier.fillMaxSize().padding(top = topGlassPadding),
                        )
                    }

                    is HomeUiState.Success -> {
                        HomePagerHost(
                            feed = state.feed,
                            pagerState = pagerState,
                            trending = trending,
                            issues = issues,
                            pulls = pulls,
                            discussions = discussions,
                            suggestedUsers = suggestedUsers,
                            hotIssues = hotIssues,
                            topGlassPadding = topGlassPadding,
                            bottomContentPadding = bottomContentPadding,
                            onFeedItemClick = onFeedItemClick,
                            onSectionScrollChanged = { contentScrolling = it },
                            onSelectIssueFilter = viewModel::selectIssueFilter,
                            onSelectPullFilter = viewModel::selectPullFilter,
                            onRetryIssues = viewModel::retryIssues,
                            onRetryPulls = viewModel::retryPulls,
                            onRetryDiscussions = viewModel::retryDiscussions,
                            onRetryExplore = viewModel::retryExplore,
                        )
                    }
                }
            }
        }
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

/**
 * 首页玻璃头（issue #83）：顶栏 + 可滚动分区条，同处一块玻璃矩形。
 *
 * - zIndex 让玻璃头在内容（hazeSource）之后绘制 —— Haze 要求 source 先画；
 * - 分区条走 AppTopBar 的 sectionBar 副行插槽，因此在玻璃矩形之内：Scaffold 交给内容区的
 *   top inset 自动含副行高度，列表视口才铺得到玻璃背后；
 * - 分区条选中项随 Pager 目标页即时跟随（滑动跟手），点条滚页由调用方注入。
 */
@Composable
private fun HomeGlassHeader(
    tabClickTicks: Int,
    pagerState: PagerState,
    blurEnabled: Boolean,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTabClick: () -> Unit,
    onSelectTab: (Int) -> Unit,
) {
    // 保留点击信号入参：fade-through 过渡消费（#167 / UI16 契约），
    // 避免「点击无反馈」的回归 —— 过渡本身由 Pager 跟手滚动表达。
    @Suppress("UNUSED_EXPRESSION")
    tabClickTicks
    Box(modifier = Modifier.zIndex(1f)) {
        AppTopBar(
            onSearchClick = onSearchClick,
            onNotificationClick = onNotificationClick,
            onProfileClick = onProfileClick,
            blurEnabled = blurEnabled,
            sectionBar = {
                AppScrollableTabs(
                    tabs =
                        HomeTab.entries.map { tab ->
                            AppTab(label = stringResource(tab.titleRes), icon = tab.icon)
                        },
                    selectedIndex = pagerState.targetPage.coerceIn(0, HomeTab.entries.lastIndex),
                    onSelect = { index ->
                        onTabClick()
                        onSelectTab(index)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    showDivider = false,
                )
            },
        )
    }
}

/** 五分区 Pager：每页一个分区内容。 */
@Composable
private fun HomePagerHost(
    feed: Flow<PagingData<FeedItem>>,
    pagerState: PagerState,
    trending: List<TrendItem>,
    issues: HomeSectionState<HomeIssueItem>,
    pulls: HomeSectionState<HomeIssueItem>,
    discussions: HomeSectionState<HomeDiscussionItem>,
    suggestedUsers: HomeSectionState<HomeUserItem>,
    hotIssues: HomeSectionState<HomeIssueItem>,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onFeedItemClick: (ParsedUrl) -> Unit,
    onSectionScrollChanged: (Boolean) -> Unit,
    onSelectIssueFilter: (HomeIssueFilter) -> Unit,
    onSelectPullFilter: (HomePullFilter) -> Unit,
    onRetryIssues: () -> Unit,
    onRetryPulls: () -> Unit,
    onRetryDiscussions: () -> Unit,
    onRetryExplore: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        when (HomeTab.entries[page]) {
            HomeTab.FEED -> {
                FeedSection(
                    feed = feed,
                    topGlassPadding = topGlassPadding,
                    bottomContentPadding = bottomContentPadding,
                    onFeedItemClick = onFeedItemClick,
                    onScrollChanged = onSectionScrollChanged,
                )
            }

            HomeTab.ISSUES -> {
                IssueSection(
                    state = issues,
                    emptyTitle = stringResource(R.string.home_empty_issues),
                    emptyMessage = stringResource(R.string.home_empty_issues_desc),
                    filters =
                        listOf(
                            stringResource(R.string.home_filter_assigned),
                            stringResource(R.string.home_filter_created),
                            stringResource(R.string.home_filter_mentioned),
                        ),
                    onSelectFilter = { index -> onSelectIssueFilter(HomeIssueFilter.entries[index]) },
                    onRetry = onRetryIssues,
                    onItemClick = onFeedItemClick,
                    topGlassPadding = topGlassPadding,
                    bottomContentPadding = bottomContentPadding,
                    onScrollChanged = onSectionScrollChanged,
                )
            }

            HomeTab.PULL_REQUESTS -> {
                IssueSection(
                    state = pulls,
                    emptyTitle = stringResource(R.string.home_empty_pulls),
                    emptyMessage = stringResource(R.string.home_empty_pulls_desc),
                    filters =
                        listOf(
                            stringResource(R.string.home_filter_involved),
                            stringResource(R.string.home_filter_review),
                            stringResource(R.string.home_filter_recent),
                        ),
                    onSelectFilter = { index -> onSelectPullFilter(HomePullFilter.entries[index]) },
                    onRetry = onRetryPulls,
                    onItemClick = onFeedItemClick,
                    topGlassPadding = topGlassPadding,
                    bottomContentPadding = bottomContentPadding,
                    onScrollChanged = onSectionScrollChanged,
                )
            }

            HomeTab.DISCUSSIONS -> {
                DiscussionSection(
                    state = discussions,
                    onRetry = onRetryDiscussions,
                    onItemClick = onFeedItemClick,
                    topGlassPadding = topGlassPadding,
                    bottomContentPadding = bottomContentPadding,
                    onScrollChanged = onSectionScrollChanged,
                )
            }

            HomeTab.EXPLORE -> {
                ExploreSection(
                    trending = trending,
                    suggestedUsers = suggestedUsers,
                    hotIssues = hotIssues,
                    onRetry = onRetryExplore,
                    onItemClick = onFeedItemClick,
                    topGlassPadding = topGlassPadding,
                    bottomContentPadding = bottomContentPadding,
                    onScrollChanged = onSectionScrollChanged,
                )
            }
        }
    }
}

/** 动态分区：分页动态流（FeedRow 自带卡片）。 */
@Composable
private fun FeedSection(
    feed: Flow<PagingData<FeedItem>>,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onFeedItemClick: (ParsedUrl) -> Unit,
    onScrollChanged: (Boolean) -> Unit,
) {
    val pagingItems = feed.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    ReportScroll(listState, onScrollChanged)
    // 首载骨架（UI-7）：refresh 中且一行都还没有 → 骨架列表（与旧版同一行为契约）
    val firstLoad = pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.Loading
    if (firstLoad) {
        FeedSkeleton(modifier = Modifier.fillMaxSize().padding(top = topGlassPadding))
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = topGlassPadding,
                start = AppDimens.spacing.l,
                end = AppDimens.spacing.l,
                bottom = AppDimens.spacing.s + bottomContentPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listSpacing),
    ) {
        items(pagingItems.itemCount) { index ->
            pagingItems[index]?.let { item ->
                FeedRow(item = item, onClick = { onFeedItemClick(item.parsed()) })
            }
        }
    }
}

/** 议题 / 拉取请求分区：子过滤 chips + 行列表。 */
@Composable
private fun IssueSection(
    state: HomeSectionState<HomeIssueItem>,
    emptyTitle: String,
    emptyMessage: String,
    filters: List<String>,
    onSelectFilter: (Int) -> Unit,
    onRetry: () -> Unit,
    onItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onScrollChanged: (Boolean) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppFilterChipRow(
            options = filters,
            selectedIndex = selectedFilter,
            onSelect = { index ->
                selectedFilter = index
                onSelectFilter(index)
            },
            contentPadding = PaddingValues(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.s),
            modifier = Modifier.padding(top = topGlassPadding),
        )
        SectionHost(
            state = state,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            onRetry = onRetry,
            bottomContentPadding = bottomContentPadding,
            onScrollChanged = onScrollChanged,
        ) { list ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = AppDimens.spacing.l,
                        end = AppDimens.spacing.l,
                        bottom = bottomContentPadding + AppDimens.spacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.listSpacing),
            ) {
                items(list, key = { it.id }) { item ->
                    AppCard(onClick = { onItemClick(item.parsed()) }, contentPadding = PaddingValues(0.dp)) {
                        HomeIssueRow(item = item)
                    }
                }
            }
        }
    }
}

/** 讨论分区：GraphQL 搜索结果（分类 / 已解答 / 评论数）。 */
@Composable
private fun DiscussionSection(
    state: HomeSectionState<HomeDiscussionItem>,
    onRetry: () -> Unit,
    onItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onScrollChanged: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = topGlassPadding)) {
        SectionHost(
            state = state,
            emptyTitle = stringResource(R.string.home_empty_discussions),
            emptyMessage = stringResource(R.string.home_empty_discussions_desc),
            onRetry = onRetry,
            bottomContentPadding = bottomContentPadding,
            onScrollChanged = onScrollChanged,
        ) { list ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = AppDimens.spacing.l,
                        end = AppDimens.spacing.l,
                        top = AppDimens.spacing.s,
                        bottom = bottomContentPadding + AppDimens.spacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.listSpacing),
            ) {
                items(list, key = { it.id }) { item ->
                    AppCard(onClick = { onItemClick(item.parsed()) }, contentPadding = PaddingValues(0.dp)) {
                        HomeDiscussionRow(item = item)
                    }
                }
            }
        }
    }
}

/** 发现分区：主题 chips + 趋势仓库 + 推荐关注（分组列表）+ 热门议题。 */
@Composable
private fun ExploreSection(
    trending: List<TrendItem>,
    suggestedUsers: HomeSectionState<HomeUserItem>,
    hotIssues: HomeSectionState<HomeIssueItem>,
    onRetry: () -> Unit,
    onItemClick: (ParsedUrl) -> Unit,
    topGlassPadding: Dp,
    bottomContentPadding: Dp,
    onScrollChanged: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    ReportScroll(listState, onScrollChanged)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topGlassPadding, bottom = bottomContentPadding + AppDimens.spacing.xl),
    ) {
        item(key = "topics") {
            Column {
                AppSectionHeader(title = stringResource(R.string.home_section_topics))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AppDimens.spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
                ) {
                    items(EXPLORE_TOPICS) { topic ->
                        AppStatPill(text = "#" + topic, icon = AppIcons.Label.rounded)
                    }
                }
            }
        }

        item(key = "trending") {
            TrendingSection(
                items = trending,
                onItemClick = { onItemClick(it.parsed()) },
                modifier = Modifier.padding(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.s),
            )
        }

        item(key = "users-header") {
            AppSectionHeader(title = stringResource(R.string.home_section_users))
        }
        when (suggestedUsers) {
            is HomeSectionState.Loading -> {
                item(key = "users-loading") { AppListSkeleton(rows = 3, rowHeight = 72.dp) }
            }

            is HomeSectionState.Error -> {
                item(key = "users-error") {
                    AppErrorState(
                        title = stringResource(R.string.feed_error_unknown),
                        actionLabel = stringResource(R.string.feed_retry),
                        onAction = onRetry,
                    )
                }
            }

            is HomeSectionState.Data -> {
                item(key = "users") {
                    AppGroupedColumn(items = suggestedUsers.items) { user, _ ->
                        AppListRow(
                            title = user.name?.takeIf { it.isNotBlank() } ?: user.login,
                            subtitle = "@" + user.login,
                            leading = {
                                AppIconBadge(
                                    icon = AppIcons.Person.rounded,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            },
                            trailingChevron = true,
                            onClick = { onItemClick(ParsedUrl.User(user.login)) },
                        )
                    }
                }
            }
        }

        item(key = "issues-header") {
            AppSectionHeader(title = stringResource(R.string.home_section_hot_issues))
        }
        when (hotIssues) {
            is HomeSectionState.Loading -> {
                item(key = "issues-loading") { AppListSkeleton(rows = 3, rowHeight = 84.dp) }
            }

            is HomeSectionState.Error -> {
                item(key = "issues-error") {
                    AppErrorState(
                        title = stringResource(R.string.feed_error_unknown),
                        actionLabel = stringResource(R.string.feed_retry),
                        onAction = onRetry,
                    )
                }
            }

            is HomeSectionState.Data -> {
                items(hotIssues.items, key = { "hot-" + it.id }) { item ->
                    Box(
                        modifier =
                            Modifier.padding(
                                horizontal = AppDimens.spacing.l,
                                vertical = AppDimens.listSpacing / 2,
                            ),
                    ) {
                        AppCard(onClick = { onItemClick(item.parsed()) }, contentPadding = PaddingValues(0.dp)) {
                            HomeIssueRow(item = item)
                        }
                    }
                }
            }
        }
    }
}

/** 分区三态宿主：骨架 / 错误 + 重试 / 空态 / 内容。 */
@Composable
private fun <T> SectionHost(
    state: HomeSectionState<T>,
    emptyTitle: String,
    emptyMessage: String,
    onRetry: () -> Unit,
    bottomContentPadding: Dp,
    onScrollChanged: (Boolean) -> Unit,
    content: @Composable (List<T>) -> Unit,
) {
    when (state) {
        is HomeSectionState.Loading -> {
            val listState = rememberLazyListState()
            ReportScroll(listState, onScrollChanged)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item { AppListSkeleton(rows = 5) }
            }
        }

        is HomeSectionState.Error -> {
            AppErrorState(
                title = stringResource(R.string.feed_error_unknown),
                actionLabel = stringResource(R.string.feed_retry),
                onAction = onRetry,
                modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding),
            )
        }

        is HomeSectionState.Data -> {
            if (state.items.isEmpty()) {
                AppEmptyState(
                    icon = AppDevOcticons.Info,
                    title = emptyTitle,
                    message = emptyMessage,
                    modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding),
                )
            } else {
                content(state.items)
            }
        }
    }
}

/** 议题/PR 行（状态图标 + 标题 + label + 归属/编号/时间/评论数）。 */
@Composable
private fun HomeIssueRow(item: HomeIssueItem) {
    val extended = MaterialTheme.extendedColors
    val stateColor =
        when {
            item.isMerged -> extended.merged
            item.state == "open" -> extended.success
            else -> extended.danger
        }
    val stateIcon =
        when {
            item.isMerged -> AppDevOcticons.Merge
            item.isPullRequest -> AppDevOcticons.PullRequest
            item.state == "open" -> AppDevOcticons.IssueOpened
            else -> AppDevOcticons.Check
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(AppDimens.spacing.l),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconBadge(
            icon = stateIcon,
            containerColor = stateColor.copy(alpha = 0.14f),
            contentColor = stateColor,
        )
        Spacer(Modifier.width(AppDimens.spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.labels.isNotEmpty()) {
                Spacer(Modifier.height(AppDimens.spacing.s))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs + 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item.labels.take(3).forEach { label -> HomeLabelPill(label) }
                }
            }
            Spacer(Modifier.height(AppDimens.spacing.s))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
            ) {
                if (item.repoFullName.isNotBlank()) {
                    Text(
                        text = item.repoFullName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = "#" + item.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.updatedAt?.let { iso ->
                    Text(
                        text = relativeTimeText(iso) ?: iso,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (item.commentCount > 0) {
                    AppIcon(spec = AppIcons.ChatBubble, contentDescription = null, size = 14.dp)
                    Text(
                        text = item.commentCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** GitHub label 胶囊（颜色来自 API，文本色按亮度取黑/白）。 */
@Composable
private fun HomeLabelPill(label: HomeLabel) {
    val (background, content) = labelColors(label.colorHex)
    AppStatPill(text = label.name, containerColor = background, contentColor = content)
}

/** 讨论行（分类 + 已解答 + 评论数）。 */
@Composable
private fun HomeDiscussionRow(item: HomeDiscussionItem) {
    val extended = MaterialTheme.extendedColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(AppDimens.spacing.l),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconBadge(
            icon = AppIcons.Forum.rounded,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(AppDimens.spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(AppDimens.spacing.s))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
            ) {
                item.repoFullName?.takeIf { it.isNotBlank() }?.let { repo ->
                    Text(
                        text = repo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                item.categoryName?.let { category -> AppStatPill(text = category.orEmpty()) }
                if (item.isAnswered) {
                    Text(
                        text = stringResource(R.string.home_discussion_answered),
                        style = MaterialTheme.typography.labelMedium,
                        color = extended.success,
                    )
                }
                Text(
                    text = item.commentCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppChevron()
            }
        }
    }
}

/** 把列表滚动状态上报给宿主（FAB 的滑动感知收纳）。 */
@Composable
private fun ReportScroll(
    listState: LazyListState,
    onScrollChanged: (Boolean) -> Unit,
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollChanged)
    }
}

/** 错误文案（网络/未知）。 */
@Composable
private fun errorMessage(errorType: HomeErrorType): String =
    when (errorType) {
        HomeErrorType.NETWORK -> stringResource(R.string.feed_error_network)
        HomeErrorType.UNKNOWN -> stringResource(R.string.feed_error_unknown)
    }

// ── 模型 → 应用内路由（复用 feed 的 ParsedUrl 通道，宿主接线零改动）─────────────

/** 议题/PR 行 → 详情路由（PR 走 PullRequest 变体）。 */
internal fun HomeIssueItem.parsed(): ParsedUrl {
    val owner = repoFullName.substringBefore('/')
    val repo = repoFullName.substringAfter('/', "")
    if (owner.isBlank() || repo.isBlank()) {
        return ParsedUrl.External("https://github.com/" + repoFullName)
    }
    return if (isPullRequest) {
        ParsedUrl.PullRequest(owner, repo, number)
    } else {
        ParsedUrl.Issue(owner, repo, number)
    }
}

/** 讨论行 → 讨论详情路由（应用内 Discussions 屏）。 */
internal fun HomeDiscussionItem.parsed(): ParsedUrl {
    val full = repoFullName.orEmpty()
    val owner = full.substringBefore('/')
    val repo = full.substringAfter('/', "")
    return if (owner.isBlank() || repo.isBlank()) {
        ParsedUrl.External("https://github.com/" + full + "/discussions/" + number)
    } else {
        ParsedUrl.Discussion(owner, repo, number)
    }
}

/** 趋势仓库 → 仓库详情路由。 */
internal fun TrendItem.parsed(): ParsedUrl {
    val owner = fullName.substringBefore('/')
    val repo = fullName.substringAfter('/', "")
    return if (owner.isBlank() || repo.isBlank()) ParsedUrl.External(url) else ParsedUrl.Repo(owner, repo)
}

/** 动态流条目 → 应用内路由（沿用既有 html_url 解析）。 */
internal fun FeedItem.parsed(): ParsedUrl =
    htmlUrl?.let { GitHubLinkParser.parseUrl(it) }
        ?: ParsedUrl.External("https://github.com/" + repoFullName)
