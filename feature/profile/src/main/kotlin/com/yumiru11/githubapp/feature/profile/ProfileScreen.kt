package com.yumiru11.githubapp.feature.profile

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.ui.AppSnackbarHost
import com.yumiru11.githubapp.core.ui.sharedTransitionElement
import kotlinx.coroutines.launch

/**
 * 个人主页（T20；L10 起兼作他人主页）。
 *
 * - **模式**：路由参数 login 决定——PROFILE 路由（无参）＝本人主页，保留设置入口；
 *   USER 路由（带 login）＝他人主页，只读资料头 + Follow/Unfollow（乐观更新 + 失败回滚 + Snackbar），
 *   顶栏给返回按钮、隐藏设置入口（[onBackClick] 非空即为他人主页语境）
 * - 未登录（Anonymous）→ 登录引导（onLoginClick 由宿主接线到 LOGIN 路由）
 * - 已登录 → 资料头（头像/昵称/简介/统计）+ 入口列表（Gists，L11）+ 四 Tab 列表
 *   （Repos/Starred/Followers/Following，Paging 3 分页）
 * - 空态/错态/加载态齐全；全部文案 stringResource（en + zh-rCN）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit = {},
    onOpenRepository: (owner: String, repo: String) -> Unit = { _, _ -> },
    onOpenUser: (login: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    /** 他人主页（USER 路由）传入返回动作；null = 本人主页（底栏 Tab，无返回） */
    onBackClick: (() -> Unit)? = null,
    /** Gists 入口（L11）：参数为该主页用户的 login */
    onOpenGists: (login: String) -> Unit = {},
    /** 底栏玻璃总高（MainTabPager 传入）：作为列表 contentPadding，让内容滚进玻璃背后 */
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? ProfileUiState.Success
    val isOtherUser = success != null && !success.isSelf
    val snackbarHostState = remember { SnackbarHostState() }
    val followFailedMessage = stringResource(R.string.profile_follow_failed)

    // 关注失败事件 → Snackbar（回滚由 ViewModel 完成，UI 只负责告知）
    LaunchedEffect(followFailedMessage) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileEvent.FollowActionFailed -> snackbarHostState.showSnackbar(followFailedMessage)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        // 内容延伸到底栏玻璃背后（Haze source 需真实像素 + 消除栏上方空带）
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isOtherUser && success != null) {
                        Text(text = "@${success.user.login}")
                    } else {
                        Text(text = stringResource(R.string.profile_title))
                    }
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.profile_back),
                            )
                        }
                    }
                },
                actions = {
                    // 设置入口只属于本人主页（底栏分区页）：按路由语境判定而非资料头状态，
                    // 他人主页在加载/错误/未登录态下同样不出现写入口
                    if (onBackClick == null) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.profile_settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    // #84：共享加载态组件
                    AppLoadingState(modifier = Modifier.fillMaxSize())
                }

                is ProfileUiState.Anonymous -> {
                    LoginGuide(
                        onLoginClick = onLoginClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is ProfileUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is ProfileUiState.Success -> {
                    ProfileContent(
                        state = state,
                        viewModel = viewModel,
                        onOpenRepository = onOpenRepository,
                        onOpenUser = onOpenUser,
                        onOpenGists = onOpenGists,
                        bottomContentPadding = bottomContentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * 资料头 + Tab 列表主体（单 LazyColumn：头部/统计/Tab 与列表项共享虚拟化滚动）。
 */
@Composable
private fun ProfileContent(
    state: ProfileUiState.Success,
    viewModel: ProfileViewModel,
    onOpenRepository: (owner: String, repo: String) -> Unit,
    onOpenUser: (login: String) -> Unit,
    onOpenGists: (login: String) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val user = state.user
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.REPOSITORIES) }
    // 四列表各自独立收集（cachedIn 共享缓存，切换 Tab 不重复请求）
    val repositories = viewModel.repositories.collectAsLazyPagingItems()
    val starred = viewModel.starred.collectAsLazyPagingItems()
    val followers = viewModel.followers.collectAsLazyPagingItems()
    val following = viewModel.following.collectAsLazyPagingItems()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomContentPadding),
    ) {
        item(key = "header") {
            ProfileHeader(
                user = user,
                isSelf = state.isSelf,
                isFollowing = state.isFollowing,
                onToggleFollow = viewModel::toggleFollow,
            )
        }
        item(key = "entries") {
            // 入口列表（ui-design §3.5）：Gists（L11）；Bookmarks/关于属后续版本
            ProfileEntries(onOpenGists = { onOpenGists(user.login) })
        }
        item(key = "tabs") {
            ProfileTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        }
        when (selectedTab) {
            ProfileTab.REPOSITORIES -> repositoryListItems(repositories, onOpenRepository)
            ProfileTab.STARRED -> repositoryListItems(starred, onOpenRepository)
            ProfileTab.FOLLOWERS -> userListItems(followers, onOpenUser)
            ProfileTab.FOLLOWING -> userListItems(following, onOpenUser)
        }
    }
}

/**
 * 资料头：头像/昵称/@login/简介/统计（仓库数/关注者/关注中；REST /user 无 Starred 计数）。
 *
 * 他人主页（[isSelf] = false）在统计行下方补 Follow/Unfollow 按钮（L10）：
 * 文案随 [isFollowing] 切换，点击交给 ViewModel 乐观更新。
 */
@Composable
private fun ProfileHeader(
    user: User,
    isSelf: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ProfileAvatar(avatarUrl = user.avatarUrl)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = user.name ?: user.login,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "@${user.login}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val bio = user.bio
        if (!bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // 统计数字变化用 AnimatedContent 滚动（#167 / UI09，§3.5「统计数字 AnimatedContent 滚动」）：
        // 关注/取关后 followers 会变，数字直接跳变会显得"闪"，滚动过渡更稳。
        Row {
            StatNumber(
                value = user.publicRepos,
                labelRes = R.string.profile_stats_repos,
            )
            Spacer(modifier = Modifier.width(24.dp))
            StatNumber(
                value = user.followers,
                labelRes = R.string.profile_stats_followers,
            )
            Spacer(modifier = Modifier.width(24.dp))
            StatNumber(
                value = user.following,
                labelRes = R.string.profile_stats_following,
            )
            // Star 总数（#166 / UI21）：REST 资料端点不返回，取不到时**整项不渲染**——
            // 不拿 0 冒充（"0 stars" 与"未知"在用户眼里是两回事）。
            user.starredCount?.let { stars ->
                Spacer(modifier = Modifier.width(24.dp))
                StatNumber(
                    value = stars,
                    labelRes = R.string.profile_stats_stars,
                )
            }
        }
        if (!isSelf) {
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onToggleFollow) {
                Text(
                    text =
                        stringResource(
                            if (isFollowing) R.string.profile_unfollow else R.string.profile_follow,
                        ),
                )
            }
        }
    }
}

/** 入口列表（ui-design §3.5）：Gists（L11）——ListItem + 前置 Octicons + 尾随指示箭头 */
@Composable
private fun ProfileEntries(onOpenGists: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.profile_gists_entry)) },
            leadingContent = {
                Icon(
                    imageVector = AppDevOcticons.File,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier =
                Modifier.clickable(
                    onClickLabel = stringResource(R.string.profile_gists_entry),
                    onClick = onOpenGists,
                ),
        )
        HorizontalDivider()
    }
}

@Composable
private fun StatItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 统计数字（#167 / UI09，ui-design §3.5「统计数字 AnimatedContent 滚动」）。
 *
 * 数字变化（例如关注后 followers +1）时用 [AnimatedContent] 做滑动过渡：直接跳变在
 * 统计行里很显眼，滚动一格的观感更接近原生 M3。
 * 动效时长走 [AppMotion.scaledDuration]（系统「减弱动画」下退化为瞬时切换）。
 */
@Composable
private fun StatNumber(
    value: Int,
    @StringRes labelRes: Int,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically { height -> height } + fadeIn())
                .togetherWith(slideOutVertically { height -> -height } + fadeOut())
        },
        label = "profile-stat",
    ) { current ->
        StatItem(text = stringResource(labelRes, current))
    }
}

/** 四 Tab 选择器（Repos/Starred/Followers/Following） */
@Composable
private fun ProfileTabs(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        ProfileTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}

/** 仓库列表项（Repos/Starred 共用）：加载/空/错态 + 分页项 */
private fun LazyListScope.repositoryListItems(
    items: LazyPagingItems<Repository>,
    onOpenRepository: (owner: String, repo: String) -> Unit,
) {
    when {
        items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> {
            item(key = "loading") { ListLoadingRow() }
        }

        items.loadState.refresh is LoadState.Error && items.itemCount == 0 -> {
            item(key = "error") { ListErrorRow(onRetry = { items.retry() }) }
        }

        items.itemCount == 0 -> {
            item(key = "empty") { ListEmptyRow(icon = AppDevOcticons.Repo) }
        }

        else -> {
            items(count = items.itemCount) { index ->
                items[index]?.let { repository ->
                    RepositoryRow(
                        repository = repository,
                        onClick = { onOpenRepository(repository.ownerLogin, repository.name) },
                    )
                }
            }
            if (items.loadState.append is LoadState.Loading) {
                item(key = "append-loading") { ListLoadingRow() }
            }
            if (items.loadState.append is LoadState.Error) {
                item(key = "append-error") { ListErrorRow(onRetry = { items.retry() }) }
            }
        }
    }
}

/** 用户列表项（Followers/Following 共用）：加载/空/错态 + 分页项 */
private fun LazyListScope.userListItems(
    items: LazyPagingItems<User>,
    onOpenUser: (login: String) -> Unit,
) {
    when {
        items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> {
            item(key = "loading") { ListLoadingRow() }
        }

        items.loadState.refresh is LoadState.Error && items.itemCount == 0 -> {
            item(key = "error") { ListErrorRow(onRetry = { items.retry() }) }
        }

        items.itemCount == 0 -> {
            item(key = "empty") { ListEmptyRow(icon = AppDevOcticons.Eye) }
        }

        else -> {
            items(count = items.itemCount) { index ->
                items[index]?.let { user ->
                    UserRow(
                        user = user,
                        onClick = { onOpenUser(user.login) },
                    )
                }
            }
            if (items.loadState.append is LoadState.Loading) {
                item(key = "append-loading") { ListLoadingRow() }
            }
            if (items.loadState.append is LoadState.Error) {
                item(key = "append-error") { ListErrorRow(onRetry = { items.retry() }) }
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    repository: Repository,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = "https://github.com/${repository.ownerLogin}.png",
            contentDescription = stringResource(R.string.profile_avatar),
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    // #90 共享元素试点：与 RepoDetail RepoHeader 头像同 key，点击仓库平滑放大
                    .sharedTransitionElement(key = "repo-avatar-${repository.ownerLogin}"),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = repository.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = repository.description
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = repository.stargazerCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val language = repository.language
                if (!language.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun UserRow(
    user: User,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = stringResource(R.string.profile_avatar),
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = user.name ?: user.login,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.login}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun ListLoadingRow() {
    // #84：共享加载态组件
    AppLoadingState(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
    )
}

/** 空态行：图标按列表语境传入（仓库列表 Repo / 用户列表 Eye），文案本地化 */
@Composable
private fun ListEmptyRow(icon: ImageVector) {
    AppEmptyState(
        icon = icon,
        title = stringResource(R.string.profile_list_empty),
        modifier =
            Modifier
                .fillMaxWidth(),
    )
}

@Composable
private fun ListErrorRow(onRetry: () -> Unit) {
    // #84：共享错误态组件（Alert 插图 + 文案 + 重试按钮）
    AppErrorState(
        title = stringResource(R.string.profile_list_error),
        actionLabel = stringResource(R.string.profile_list_retry),
        onAction = onRetry,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
    )
}

/** 未登录引导：标题/说明/登录按钮（onLoginClick 由宿主接线） */
@Composable
private fun LoginGuide(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_login_guide_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profile_login_guide_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLoginClick) {
            Text(stringResource(R.string.profile_sign_in))
        }
    }
}

/**
 * 资料头加载失败（错误类型驱动文案）。
 *
 * 设计系统 Batch 2：收敛到共享 [AppErrorState]（Alert 插图 + 标题 + 重试），
 * 不再手搓无插图的原文色小字 + 裸按钮。
 */
@Composable
private fun ErrorContent(
    errorType: ProfileErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppErrorState(
        title = stringResource(errorType.messageRes()),
        actionLabel = stringResource(R.string.profile_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** 错误类型 → 本地化文案资源（ViewModel 不产英文文案） */
private fun ProfileErrorType.messageRes(): Int =
    when (this) {
        ProfileErrorType.NOT_FOUND -> R.string.profile_error_not_found
        ProfileErrorType.NETWORK -> R.string.profile_error_network
        ProfileErrorType.UNKNOWN -> R.string.profile_error_unknown
    }

/** 四列表 Tab 枚举（titleRes 驱动 Tab 文案） */
private enum class ProfileTab(
    val titleRes: Int,
) {
    REPOSITORIES(R.string.profile_tab_repositories),
    STARRED(R.string.profile_tab_starred),
    FOLLOWERS(R.string.profile_tab_followers),
    FOLLOWING(R.string.profile_tab_following),
}

/** 头像点击回弹峰值（#167 / UI09：§3.5 用户拍板 1.1x） */
private const val AVATAR_BOUNCE_SCALE = 1.1f

/**
 * 资料头头像（#167 / UI09，ui-design §3.5「头像点击 1.1x 回弹」+ §4.3 spring）。
 *
 * 纯反馈动效，不导航：头像本身没有"更进一步的去处"，点击跳转会造成意外。
 * 单独成组件是为了让 [ProfileHeader] 保持在 detekt 的方法长度上限内。
 */
@Composable
private fun ProfileAvatar(avatarUrl: String?) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    AsyncImage(
        model = avatarUrl,
        contentDescription = stringResource(R.string.profile_avatar),
        modifier =
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // 播一次 1.0 → 1.1 → 1.0；已在播放中（value ≠ 1）就忽略重复点击
                    scope.launch {
                        if (scale.value == 1f) {
                            scale.animateTo(
                                AVATAR_BOUNCE_SCALE,
                                spring(dampingRatio = AppMotion.DampingRatioHighBouncy, stiffness = AppMotion.StiffnessMedium),
                            )
                            scale.animateTo(
                                1f,
                                spring(dampingRatio = AppMotion.DampingRatioHighBouncy, stiffness = AppMotion.StiffnessMedium),
                            )
                        }
                    }
                },
        contentScale = ContentScale.Crop,
    )
}
