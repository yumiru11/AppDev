@file:Suppress("LongMethod", "LongParameterList")
// 列表/网格两套卡片 + 长按菜单 + 三态占位集中在一个文件里更易对照（同 RepoDetailScreen 先例）。

package com.yumiru11.githubapp.feature.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Grid_view
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Share
import com.composables.icons.materialsymbols.rounded.View_list
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.datastore.model.RepoLayoutMode
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.component.LocalHazeState
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import com.yumiru11.githubapp.core.ui.AppTopBar
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 参与首屏 stagger 的最大行数（超出者直出，避免深页滚动反复入场） */
private const val STAGGER_MAX_ITEMS = 12

/** 进入动效的位移量（slide-up 起点） */
private val ENTER_SLIDE_DISTANCE = 12.dp

/**
 * 「仓库」大分区（#166 / UI01+UI02，ui-design §3.2）。
 *
 * 视觉契约：
 * - 顶栏沿用全局 [AppTopBar]（搜索/铃铛/头像常驻），第二行（sectionBar 插槽）放
 *   标题 + 仓库数 + **布局切换按钮**——与首页小分区条共用同一块玻璃（§6.1 #83 补充）
 * - 内容 full-bleed，insets 走滚动容器 contentPadding，让列表物理滚进玻璃背后
 * - 列表项：Octicons Repo 图标 + 名称 + 描述（2 行）+ 星标数/语言/私有标记；
 *   **长按弹出菜单**（§3.2 用户拍板：不用左滑）
 * - 布局切换走 Crossfade（§3.2「布局切换 Crossfade」）
 *
 * @param onOpenRepository 点击仓库 → 仓库详情
 * @param onLoginClick 游客态登录引导
 */
@Composable
fun ReposScreen(
    onOpenRepository: (owner: String, repo: String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: ReposViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    val repositories = viewModel.repositories.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 结果反馈（收藏成功/失败）——文案在 UI 层解析，VM 只发事件
    val starToggledTemplate = stringResource(R.string.repos_star_added)
    val starRemovedTemplate = stringResource(R.string.repos_star_removed)
    val starFailed = stringResource(R.string.repos_star_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    is ReposEvent.StarToggled -> {
                        if (event.starred) {
                            starToggledTemplate.format(event.repository)
                        } else {
                            starRemovedTemplate.format(event.repository)
                        }
                    }

                    ReposEvent.StarFailed -> {
                        starFailed
                    }
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    // 顶栏玻璃接线（与 HomeScreen 同契约，issue #83）：内容侧 hazeSource + 栏侧 hazeEffect
    val hazeState = rememberHazeState()
    val blurEnabled = LocalGlassSettings.current.enabledFor(GlassScope.TOP_BAR)
    val useHazeSource = GlassRenderPolicy.shouldAttachHazeSource(blurEnabled)

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Box(modifier = Modifier.graphicsLayer { }) {
                    AppTopBar(
                        onSearchClick = onSearchClick,
                        onNotificationClick = onNotificationClick,
                        onProfileClick = onProfileClick,
                        sectionBar = {
                            ReposSectionBar(
                                layout = layout,
                                onToggleLayout = viewModel::toggleLayout,
                            )
                        },
                    )
                }
            },
        ) { paddingValues ->
            val topPadding = paddingValues.calculateTopPadding()
            val bottomPadding = paddingValues.calculateBottomPadding()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (useHazeSource) {
                                Modifier.hazeSource(hazeState)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                if (isAnonymous) {
                    AppEmptyState(
                        icon = AppDevOcticons.Repo,
                        title = stringResource(R.string.repos_login_title),
                        message = stringResource(R.string.repos_login_message),
                        actionLabel = stringResource(R.string.repos_login_action),
                        onAction = onLoginClick,
                        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
                    )
                    return@Box
                }

                val loadState = repositories.loadState.refresh
                when {
                    loadState is LoadState.Loading && repositories.itemCount == 0 -> {
                        AppLoadingState(modifier = Modifier.padding(top = topPadding))
                    }

                    loadState is LoadState.Error && repositories.itemCount == 0 -> {
                        AppErrorState(
                            title = stringResource(R.string.repos_error_title),
                            message = stringResource(R.string.repos_error_message),
                            actionLabel = stringResource(R.string.repos_error_retry),
                            onAction = { repositories.retry() },
                            modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
                        )
                    }

                    repositories.itemCount == 0 -> {
                        AppEmptyState(
                            icon = AppDevOcticons.Repo,
                            title = stringResource(R.string.repos_empty_title),
                            message = stringResource(R.string.repos_empty_message),
                            modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
                        )
                    }

                    else -> {
                        Crossfade(targetState = layout, label = "repo-layout") { mode ->
                            RepoCollection(
                                mode = mode,
                                repositories = repositories,
                                contentPadding =
                                    PaddingValues(
                                        top = topPadding + AppDimens.cornerSmall,
                                        bottom = bottomPadding + AppDimens.contentPadding,
                                    ),
                                onOpenRepository = onOpenRepository,
                                onCopyLink = { repository ->
                                    copyToClipboard(context, repository.htmlUrl())
                                    Unit
                                },
                                onOpenInBrowser = { repository ->
                                    CustomTabsIntent
                                        .Builder()
                                        .build()
                                        .launchUrl(context, Uri.parse(repository.htmlUrl()))
                                },
                                onShare = { repository -> shareRepository(context, repository) },
                                onToggleStar = { repository ->
                                    viewModel.toggleStar(repository.ownerLogin, repository.name)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 顶栏第二行：标题 + 布局切换按钮（与首页小分区条共用同一块玻璃） */
@Composable
private fun ReposSectionBar(
    layout: RepoLayoutMode,
    onToggleLayout: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = AppDimens.contentPadding, end = AppDimens.cornerSmall, bottom = AppDimens.cornerSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.repos_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        val toGrid = layout == RepoLayoutMode.LIST
        IconButton(onClick = onToggleLayout) {
            Icon(
                // 未选中的目标形态作为图标：显示"点了会变成什么"（原生设置惯例）
                // 图标 = 点击后**会变成**的形态（原生设置惯例）：当前通栏 → 显示网格图标
                imageVector = if (toGrid) MaterialSymbols.Rounded.Grid_view else MaterialSymbols.Rounded.View_list,
                contentDescription =
                    stringResource(
                        if (toGrid) R.string.repos_layout_switch_to_grid else R.string.repos_layout_switch_to_list,
                    ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 通栏 / 网格两种布局的容器（Crossfade 的两端） */
@Composable
private fun RepoCollection(
    mode: RepoLayoutMode,
    repositories: LazyPagingItems<Repository>,
    contentPadding: PaddingValues,
    onOpenRepository: (String, String) -> Unit,
    onCopyLink: (Repository) -> Unit,
    onOpenInBrowser: (Repository) -> Unit,
    onShare: (Repository) -> Unit,
    onToggleStar: (Repository) -> Unit,
) {
    when (mode) {
        RepoLayoutMode.LIST -> {
            val state = rememberLazyListState()
            LazyColumn(
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(AppDimens.cornerSmall),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = repositories.itemCount,
                    key = { index -> repositories.peek(index)?.fullName ?: index },
                ) { index ->
                    val repository = repositories[index]
                    if (repository != null) {
                        RepoListCard(
                            repository = repository,
                            index = index,
                            onClick = { onOpenRepository(repository.ownerLogin, repository.name) },
                            onCopyLink = { onCopyLink(repository) },
                            onOpenInBrowser = { onOpenInBrowser(repository) },
                            onShare = { onShare(repository) },
                            onToggleStar = { onToggleStar(repository) },
                        )
                    }
                }
                if (repositories.loadState.append is LoadState.Loading) {
                    item { PagingFooter() }
                }
            }
        }

        RepoLayoutMode.GRID -> {
            val state = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = state,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(AppDimens.cornerSmall),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.cornerSmall),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = repositories.itemCount,
                    key = { index -> repositories.peek(index)?.fullName ?: index },
                ) { index ->
                    val repository = repositories[index]
                    if (repository != null) {
                        RepoGridCard(
                            repository = repository,
                            index = index,
                            onClick = { onOpenRepository(repository.ownerLogin, repository.name) },
                            onCopyLink = { onCopyLink(repository) },
                            onOpenInBrowser = { onOpenInBrowser(repository) },
                            onShare = { onShare(repository) },
                            onToggleStar = { onToggleStar(repository) },
                        )
                    }
                }
                if (repositories.loadState.append is LoadState.Loading) {
                    item { PagingFooter() }
                }
            }
        }
    }
}

/** 追加页加载指示（列表/网格共用；跨列需自行占满，故外层 fillMaxWidth） */
@Composable
private fun PagingFooter() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** 通栏卡片（ui-design §3.2 条目构成） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoListCard(
    repository: Repository,
    index: Int,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
) {
    RepoMenuAnchor(
        onCopyLink = onCopyLink,
        onOpenInBrowser = onOpenInBrowser,
        onShare = onShare,
        onToggleStar = onToggleStar,
    ) { onLongClick ->
        Surface(
            shape = RoundedCornerShape(AppDimens.cornerMedium),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.contentPadding)
                    .then(rememberEnterModifier(index))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        ) {
            Row(
                modifier = Modifier.padding(AppDimens.contentPadding),
                verticalAlignment = Alignment.Top,
            ) {
                RepoAvatar(repository)
                Spacer(modifier = Modifier.width(AppDimens.cornerMedium))
                Column(modifier = Modifier.weight(1f)) {
                    RepoNameRow(repository)
                    RepoDescription(repository)
                    Spacer(modifier = Modifier.height(AppDimens.cornerSmall))
                    RepoMetaRow(repository)
                }
            }
        }
    }
}

/** 网格卡片：名称 + 描述（3 行）+ 元信息，纵向排布 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoGridCard(
    repository: Repository,
    index: Int,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
) {
    RepoMenuAnchor(
        onCopyLink = onCopyLink,
        onOpenInBrowser = onOpenInBrowser,
        onShare = onShare,
        onToggleStar = onToggleStar,
    ) { onLongClick ->
        Surface(
            shape = RoundedCornerShape(AppDimens.cornerLarge),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(rememberEnterModifier(index))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        ) {
            Column(modifier = Modifier.padding(AppDimens.contentPadding)) {
                RepoAvatar(repository)
                Spacer(modifier = Modifier.height(AppDimens.cornerMedium))
                RepoNameRow(repository)
                RepoDescription(repository, maxLines = 3)
                Spacer(modifier = Modifier.height(AppDimens.cornerSmall))
                RepoMetaRow(repository)
            }
        }
    }
}

/**
 * 长按菜单锚点：把 [DropdownMenu] 与触发手势绑在一起的薄封装，
 * 让两种卡片共用同一份菜单定义（避免两处漂移）。
 */
@Composable
private fun RepoMenuAnchor(
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    content: @Composable (onLongClick: () -> Unit) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    Box {
        content { expanded.value = true }
        RepoContextMenu(
            expanded = expanded.value,
            onDismiss = { expanded.value = false },
            onCopyLink = onCopyLink,
            onOpenInBrowser = onOpenInBrowser,
            onShare = onShare,
            onToggleStar = onToggleStar,
        )
    }
}

/** 长按菜单项（ui-design §3.2「长按弹出小窗选功能」） */
@Composable
private fun RepoContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.repos_menu_copy_link)) },
            leadingIcon = { Icon(MaterialSymbols.Rounded.Link, contentDescription = null) },
            onClick = {
                onDismiss()
                onCopyLink()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.repos_menu_open_browser)) },
            leadingIcon = { Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpenInBrowser()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.repos_menu_share)) },
            leadingIcon = { Icon(MaterialSymbols.Rounded.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                onShare()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.repos_menu_toggle_star)) },
            leadingIcon = { Icon(AppDevOcticons.Star, contentDescription = null) },
            onClick = {
                onDismiss()
                onToggleStar()
            },
        )
    }
}

@Composable
private fun RepoAvatar(repository: Repository) {
    AsyncImage(
        model = "https://github.com/${repository.ownerLogin}.png",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape),
    )
}

@Composable
private fun RepoNameRow(repository: Repository) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = AppDevOcticons.Repo,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = repository.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (repository.isPrivate) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(AppDimens.cornerExtraSmall),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.repos_private_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RepoDescription(
    repository: Repository,
    maxLines: Int = 2,
) {
    val description = repository.description
    if (!description.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(AppDimens.cornerExtraSmall))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RepoMetaRow(repository: Repository) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
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
            Spacer(modifier = Modifier.width(AppDimens.cornerMedium))
            Text(
                text = language,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 列表项进入动效（slide-up + fade）：间隔 [AppMotion.LIST_STAGGER_INTERVAL_MILLIS]，
 * 时长 [AppMotion.DURATION_LIST_ITEM] 经动效缩放折算（设置滑杆 × 系统缩放取 min，0 = 立即完成）。
 * 已播过的行用 rememberSaveable 记账，滚动回收后不重播。
 */
@Composable
private fun rememberEnterModifier(index: Int): Modifier {
    val played = rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played.value) 1f else 0f) }
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_LIST_ITEM)
    LaunchedEffect(duration) {
        if (progress.value < 1f) {
            val delay = if (index < STAGGER_MAX_ITEMS) index * AppMotion.LIST_STAGGER_INTERVAL_MILLIS else 0
            if (delay > 0) kotlinx.coroutines.delay(delay.toLong())
            progress.animateTo(1f, tween(durationMillis = duration, easing = AppMotion.EmphasizedDecelerate))
            played.value = true
        }
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * ENTER_SLIDE_DISTANCE.toPx()
    }
}

/** 仓库网页地址（复制链接 / 浏览器打开 / 分享共用） */
private fun Repository.htmlUrl(): String = "https://github.com/$fullName"

private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("repository", text))
}

private fun shareRepository(
    context: Context,
    repository: Repository,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, repository.htmlUrl())
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
