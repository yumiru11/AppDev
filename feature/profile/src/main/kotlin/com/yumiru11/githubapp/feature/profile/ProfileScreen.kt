package com.yumiru11.githubapp.feature.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yumiru11.githubapp.core.ui.sharedTransitionElement

/**
 * 个人主页（T20）。
 *
 * - 未登录（Anonymous）→ 登录引导（onLoginClick 由宿主接线到 LOGIN 路由）
 * - 已登录 → 资料头（头像/昵称/简介/统计）+ 四 Tab 列表（Repos/Starred/Followers/Following，Paging 3 分页）
 * - 空态/错态/加载态齐全；全部文案 stringResource（en + zh-rCN）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit = {},
    onOpenRepository: (owner: String, repo: String) -> Unit = { _, _ -> },
    onOpenUser: (login: String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    /** 底栏玻璃总高（MainTabPager 传入）：作为列表 contentPadding，让内容滚进玻璃背后 */
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        // 内容延伸到底栏玻璃背后（Haze source 需真实像素 + 消除栏上方空带）
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.profile_settings))
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
                        user = state.user,
                        viewModel = viewModel,
                        onOpenRepository = onOpenRepository,
                        onOpenUser = onOpenUser,
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
    user: User,
    viewModel: ProfileViewModel,
    onOpenRepository: (owner: String, repo: String) -> Unit,
    onOpenUser: (login: String) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
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
        item(key = "header") { ProfileHeader(user) }
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

/** 资料头：头像/昵称/@login/简介/统计（仓库数/关注者/关注中；REST /user 无 Starred 计数） */
@Composable
private fun ProfileHeader(user: User) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = stringResource(R.string.profile_avatar),
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
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
        Row {
            StatItem(text = stringResource(R.string.profile_stats_repos, user.publicRepos))
            Spacer(modifier = Modifier.width(24.dp))
            StatItem(text = stringResource(R.string.profile_stats_followers, user.followers))
            Spacer(modifier = Modifier.width(24.dp))
            StatItem(text = stringResource(R.string.profile_stats_following, user.following))
        }
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

/** 资料头加载失败（错误类型驱动文案） */
@Composable
private fun ErrorContent(
    errorType: ProfileErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorType.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.profile_retry))
        }
    }
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
