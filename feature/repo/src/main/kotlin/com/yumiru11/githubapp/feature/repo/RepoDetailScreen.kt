@file:Suppress("TooManyFunctions") // 屏幕文件：多个小 Composable 是 Compose 惯用结构，拆文件反损可读性（T3 先例）

package com.yumiru11.githubapp.feature.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Call_split
import com.composables.icons.materialsymbols.rounded.Tag
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.rounded.Visibility_off
import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.LocalRepoDetailActions
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "ReadmeRender"

/**
 * 仓库详情页（T9 README 浏览 tracer bullet + T12 仓库管理）。
 *
 * 顶部：仓库元数据（名称/描述/星/分叉/语言 + 登录态 Star/Watch/Fork 按钮 + 语言栏）
 * 下方：分区 Tab（README / 文件 / Releases）
 *
 * T12：Star/Watch 乐观更新（失败回滚 + Snackbar）；Fork 权限控制；Releases/Tags 列表
 * 与 Release 详情（页内展开，不进导航）；语言栏按 Linguist 数据渲染。
 *
 * 链接分发（T9 验收第 3 条）：WebView bridge 的链接统一经
 * [RepoDetailActions] 处理——内部链接应用内导航，外部链接 CustomTabs，纯锚点忽略。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit = {},
    viewModel: RepoDetailViewModel = hiltViewModel(),
    actions: RepoDetailActions = LocalRepoDetailActions.current,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filesViewModel: RepoFilesViewModel = hiltViewModel()
    val filesState by filesViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    RepoEventSnackbar(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            RepoTopBar(owner = owner, repo = repo, onBackClick = onBackClick)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is RepoDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is RepoDetailUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is RepoDetailUiState.Success -> {
                    if (filesState.selectedPath != null) {
                        // T11：文件查看器全屏覆盖（树/README 内容隐藏，返回键回文件树）
                        FileViewerScreen(
                            fileState = filesState.fileState,
                            selectedPath = filesState.selectedPath.orEmpty(),
                            ref = state.repo.defaultBranch ?: DEFAULT_REF,
                            viewModel = filesViewModel,
                            actions = actions,
                            baseRepoUrl = buildRepoUrl(state.repo),
                            onClose = { filesViewModel.closeFile() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        RepoDetailContent(
                            state = state,
                            filesState = filesState,
                            filesViewModel = filesViewModel,
                            actions = actions,
                            onRetryReadme = { viewModel.retry() },
                            managementCallbacks =
                                RepoManagementCallbacks(
                                    onToggleStar = { viewModel.toggleStar() },
                                    onToggleWatch = { viewModel.toggleWatch() },
                                    onFork = { viewModel.fork() },
                                    onEnsureReleasesLoaded = { viewModel.ensureReleasesLoaded() },
                                    onEnsureTagsLoaded = { viewModel.ensureTagsLoaded() },
                                    onReleaseClick = { viewModel.loadReleaseDetail(it) },
                                    onCollapseRelease = { viewModel.collapseReleaseDetail() },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 仓库管理事件 → Snackbar（UI 层 stringResource 映射，ViewModel 不产文案）。
 */
@Composable
private fun RepoEventSnackbar(
    viewModel: RepoDetailViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    RepoEvent.Forked -> context.getString(R.string.repo_snackbar_forked)
                    RepoEvent.ForkPermissionDenied -> context.getString(R.string.repo_snackbar_fork_permission_denied)
                    RepoEvent.ForkAlreadyExists -> context.getString(R.string.repo_snackbar_fork_already_exists)
                    RepoEvent.ForkFailed -> context.getString(R.string.repo_snackbar_fork_failed)
                    RepoEvent.ToggleFailed -> context.getString(R.string.repo_snackbar_toggle_failed)
                }
            snackbarHostState.showSnackbar(message)
        }
    }
}

/** 顶栏：返回 + 仓库名。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoTopBar(
    owner: String,
    repo: String,
    onBackClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "$owner/$repo",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.repo_back),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun RepoDetailContent(
    state: RepoDetailUiState.Success,
    filesState: RepoFilesUiState,
    filesViewModel: RepoFilesViewModel,
    actions: RepoDetailActions,
    onRetryReadme: () -> Unit,
    managementCallbacks: RepoManagementCallbacks,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // 水平 padding 移到各子段：README 段由 EnhancedMarkdownViewer 自带 37dp
                // （用户实测目标边距）接管——全局 16 + 内层 37 = 53dp 太宽（2026-08-17 真机）
                .padding(top = 16.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            RepoHeader(
                state = state,
                onToggleStar = managementCallbacks.onToggleStar,
                onToggleWatch = managementCallbacks.onToggleWatch,
                onFork = managementCallbacks.onFork,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(Modifier.padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(text = stringResource(R.string.repo_tab_readme)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(text = stringResource(R.string.repo_tab_files)) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(text = stringResource(R.string.repo_tab_releases)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (tab) {
            0 -> {
                ReadmeSection(
                    readmeState = state.readmeState,
                    actions = actions,
                    onRetryReadme = onRetryReadme,
                    baseRepoUrl = buildRepoUrl(state.repo),
                )
            }

            1 -> {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    FileTreeSection(
                        treeState = filesState.treeState,
                        defaultBranch = state.repo.defaultBranch,
                        viewModel = filesViewModel,
                    )
                }
            }

            else -> {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ReleasesSection(
                        state = state,
                        actions = actions,
                        callbacks = managementCallbacks,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoHeader(
    state: RepoDetailUiState.Success,
    onToggleStar: () -> Unit,
    onToggleWatch: () -> Unit,
    onFork: () -> Unit,
) {
    val repo = state.repo
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "https://github.com/${repo.ownerLogin}.png",
                    contentDescription = stringResource(R.string.repo_avatar),
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    repo.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RepoStatsRow(repo = repo)

            // T12：登录态才显示操作按钮（游客只读）
            if (state.isLoggedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                ManagementButtons(
                    isStarred = state.isStarred,
                    isWatching = state.isWatching,
                    pendingAction = state.pendingAction,
                    onToggleStar = onToggleStar,
                    onToggleWatch = onToggleWatch,
                    onFork = onFork,
                )
            }

            // T12：语言栏（Linguist 数据，按字节占比渲染）
            if (state.languages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LanguageBar(languages = state.languages)
            }
        }
    }
}

/** 仓库统计行：Star 数 / Fork 数 / 主语言。 */
@Composable
private fun RepoStatsRow(repo: Repository) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.repo_stars, repo.stargazerCount),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.repo_forks, repo.forkCount),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        repo.language?.let { lang ->
            Text(
                text = stringResource(R.string.repo_language, lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Star/Watch/Fork 操作按钮行（登录态显示；pendingAction 期间禁用防重入）。
 * Material You 风格：使用 FilledTonalButton 提供适中的视觉重量，支持动画过渡。
 */
@Composable
private fun ManagementButtons(
    isStarred: Boolean,
    isWatching: Boolean,
    pendingAction: RepoAction?,
    onToggleStar: () -> Unit,
    onToggleWatch: () -> Unit,
    onFork: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onToggleStar,
            enabled = pendingAction == null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(if (isStarred) R.string.repo_unstar else R.string.repo_star),
                maxLines = 1,
            )
        }
        FilledTonalButton(
            onClick = onToggleWatch,
            enabled = pendingAction == null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector =
                    if (isWatching) {
                        MaterialSymbols.Rounded.Visibility
                    } else {
                        MaterialSymbols.Rounded.Visibility_off
                    },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(if (isWatching) R.string.repo_unwatch else R.string.repo_watch),
                maxLines = 1,
            )
        }
        FilledTonalButton(
            onClick = onFork,
            enabled = pendingAction == null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (pendingAction == RepoAction.FORK) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Call_split,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.repo_fork),
                maxLines = 1,
            )
        }
    }
}

/**
 * 语言栏：按 Linguist 字节占比渲染分段条 + 图例（语言名 + 百分比）。
 * 颜色循环取 MaterialTheme 色板（零硬编码颜色）。
 */
@Composable
private fun LanguageBar(languages: Map<String, Long>) {
    val total = languages.values.sum().toFloat()
    if (total <= 0f) return
    val colors = languageBarColors()

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
        ) {
            languages.entries.forEachIndexed { index, (_, bytes) ->
                Box(
                    modifier =
                        Modifier
                            .weight(bytes / total)
                            .fillMaxHeight()
                            .background(colors[index % colors.size]),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            languages.entries.forEachIndexed { index, (name, bytes) ->
                val percent = (bytes * 100 / total).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors[index % colors.size]),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.repo_language_percent, name, percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 语言栏分段色板（MaterialTheme 色板循环，零硬编码颜色）。 */
@Composable
private fun languageBarColors(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )

/**
 * Releases/Tags 分区（第三个 Tab）。
 *
 * 内部 FilterChip 切换 Releases/Tags 子列表；Release 点击 → 页内展开详情（不进导航）。
 * 进入分区时懒加载两个列表（ensure* 幂等：Loaded 不重复拉取）。
 */
@Composable
private fun ReleasesSection(
    state: RepoDetailUiState.Success,
    actions: RepoDetailActions,
    callbacks: RepoManagementCallbacks,
) {
    LaunchedEffect(Unit) {
        callbacks.onEnsureReleasesLoaded()
        callbacks.onEnsureTagsLoaded()
    }

    if (state.expandedReleaseId != null) {
        ReleaseDetailView(
            detailState = state.releaseDetailState,
            baseRepoUrl = buildRepoUrl(state.repo),
            actions = actions,
            onBack = callbacks.onCollapseRelease,
            onRetry = { callbacks.onReleaseClick(state.expandedReleaseId) },
        )
        return
    }

    var subTab by rememberSaveable { mutableIntStateOf(0) }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text(text = stringResource(R.string.repo_subtab_releases)) },
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text(text = stringResource(R.string.repo_subtab_tags)) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (subTab) {
            0 -> {
                ReleasesList(
                    state = state.releasesState,
                    onReleaseClick = callbacks.onReleaseClick,
                    onRetry = callbacks.onEnsureReleasesLoaded,
                )
            }

            else -> {
                TagsList(state = state.tagsState, onRetry = callbacks.onEnsureTagsLoaded)
            }
        }
    }
}

@Composable
private fun ReleasesList(
    state: ReleasesState,
    onReleaseClick: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is ReleasesState.Idle, is ReleasesState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is ReleasesState.Error -> {
            ErrorContent(
                errorType = state.errorType,
                onRetry = onRetry,
            )
        }

        is ReleasesState.Loaded -> {
            if (state.releases.isEmpty()) {
                EmptyHint(text = stringResource(R.string.repo_releases_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.releases, key = { it.id }) { release ->
                        ReleaseCard(release = release, onClick = { onReleaseClick(release.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (release.prerelease) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ReleaseBadge(text = stringResource(R.string.repo_release_prerelease))
                }
                if (release.draft) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ReleaseBadge(text = stringResource(R.string.repo_release_draft))
                }
            }
            release.name?.let { name ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = releaseMetaText(release),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Release 元信息：日期 · 作者（作者缺失时只显示日期）。 */
@Composable
private fun releaseMetaText(release: Release): String {
    val author = release.authorLogin
    val date = release.publishedAt?.let { RELEASE_DATE_FORMAT.format(it.atZone(ZoneId.systemDefault())) } ?: ""
    return when {
        date.isBlank() && author.isNullOrBlank() -> ""
        author.isNullOrBlank() -> date
        date.isBlank() -> author
        else -> stringResource(R.string.repo_release_meta, date, author)
    }
}

@Composable
private fun ReleaseBadge(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun TagsList(
    state: TagsState,
    onRetry: () -> Unit,
) {
    when (state) {
        is TagsState.Idle, is TagsState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is TagsState.Error -> {
            ErrorContent(
                errorType = state.errorType,
                onRetry = onRetry,
            )
        }

        is TagsState.Loaded -> {
            if (state.tags.isEmpty()) {
                EmptyHint(text = stringResource(R.string.repo_tags_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags, key = { it.name }) { tag ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Release 详情（页内展开，不进导航）。
 *
 * 正文用原生 [EnhancedMarkdownViewer]（短文本通道，铁律「评论列表绝不用 WebView」精神
 * 下 Release 正文同样保持原生；链接统一经 [handleParsedUrl] 分发）。
 */
@Composable
private fun ReleaseDetailView(
    detailState: ReleaseDetailState,
    baseRepoUrl: String,
    actions: RepoDetailActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.repo_release_back),
                )
            }
            Text(
                text = stringResource(R.string.repo_tab_releases),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (detailState) {
            is ReleaseDetailState.Idle, is ReleaseDetailState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is ReleaseDetailState.Error -> {
                ErrorContent(
                    errorType = detailState.errorType,
                    onRetry = onRetry,
                )
            }

            is ReleaseDetailState.Loaded -> {
                ReleaseDetailContent(
                    release = detailState.release,
                    baseRepoUrl = baseRepoUrl,
                    actions = actions,
                )
            }
        }
    }
}

/** Release 详情正文（标题/徽章/元信息/正文 Markdown）。 */
@Composable
private fun ReleaseDetailContent(
    release: Release,
    baseRepoUrl: String,
    actions: RepoDetailActions,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.tagName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (release.prerelease) {
                Spacer(modifier = Modifier.width(8.dp))
                ReleaseBadge(text = stringResource(R.string.repo_release_prerelease))
            }
            if (release.draft) {
                Spacer(modifier = Modifier.width(8.dp))
                ReleaseBadge(text = stringResource(R.string.repo_release_draft))
            }
        }
        release.name?.let { name ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = releaseMetaText(release),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        val body = release.body
        if (body.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.repo_release_body_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EnhancedMarkdownViewer(
                markdown = body,
                onInternalLink = { parsed -> handleParsedUrl(parsed, actions) },
                baseRepoUrl = baseRepoUrl,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadmeSection(
    readmeState: ReadmeState,
    actions: RepoDetailActions,
    onRetryReadme: () -> Unit,
    baseRepoUrl: String,
) {
    when (readmeState) {
        is ReadmeState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is ReadmeState.Empty -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.repo_readme_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is ReadmeState.Loaded -> {
            // 渲染通道日志（Q7 复测锚点）：logcat 过滤 ReadmeRender；Log.i 因 vivo [log.tag]=[I] 过滤 Debug 级
            Log.i(TAG, "renderMode=${readmeState.renderMode}")
            WebViewMarkdownRenderer(
                sanitizedHtml = readmeState.content,
                tokenProvider = { null },
                bridgeCallback = createBridgeCallback(actions),
                baseRepoUrl = baseRepoUrl,
                renderMode = readmeState.webViewRenderMode,
            )
        }

        is ReadmeState.Error -> {
            ErrorContent(
                errorType = readmeState.errorType,
                onRetry = onRetryReadme,
            )
        }
    }
}

/**
 * 链接统一分发：内部链接 → 应用内导航；外部链接 → CustomTabs；纯锚点（#xxx）忽略
 * （WebView 内锚点由页面自身处理，原生渲染器无滚动定位，忽略即可；
 * 文件查看器的 .md Rendered 模式共用此逻辑）。
 */
internal fun handleParsedUrl(
    parsed: ParsedUrl,
    actions: RepoDetailActions,
) {
    if (parsed is ParsedUrl.External) {
        if (parsed.url.startsWith("#")) return
        actions.onOpenExternal(parsed.url)
    } else {
        actions.onNavigateToParsedUrl(parsed)
    }
}

/**
 * WebView bridge callback：链接/复制已接线（T9 验收第 3 条），
 * 图片预览/任务列表写回留待 T14。
 */
@Suppress("EmptyFunctionBlock") // onImageClick/onCheckboxClick/onHeightChanged 为 T14 占位桩
@Composable
private fun createBridgeCallback(actions: RepoDetailActions): MarkdownBridgeCallback {
    val context = LocalContext.current
    return object : MarkdownBridgeCallback {
        override fun onExternalLink(url: String) {
            // 纯锚点（#xxx）由页面自身处理，不拦截
            if (url.startsWith("#")) return
            actions.onOpenExternal(url)
        }

        override fun onInternalLink(parsed: ParsedUrl) {
            actions.onNavigateToParsedUrl(parsed)
        }

        override fun onCodeCopy(code: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
        }

        override fun onImageClick(src: String) {}

        override fun onCheckboxClick(
            index: Int,
            checked: Boolean,
        ) {}

        override fun onHeightChanged(heightPx: Int) {}
    }
}

@Composable
internal fun ErrorContent(
    errorType: RepoErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage(errorType),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.repo_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel 只传类型，不产英文） */
@Composable
private fun errorMessage(errorType: RepoErrorType): String =
    when (errorType) {
        RepoErrorType.NOT_FOUND -> stringResource(R.string.repo_error_not_found)
        RepoErrorType.NETWORK -> stringResource(R.string.repo_error_network)
        RepoErrorType.UNKNOWN -> stringResource(R.string.repo_error_unknown)
    }

/** Repository → GitHub 仓库页 URL（相对链接解析基址，2026-08-14 修复） */
private fun buildRepoUrl(repo: Repository): String = "https://github.com/${repo.ownerLogin}/${repo.name}"

/** Release 发布日期格式（yyyy-MM-dd，本地时区） */
private val RELEASE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
