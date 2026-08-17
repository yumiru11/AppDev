package com.yumiru11.githubapp.feature.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.LocalRepoDetailActions
import com.yumiru11.githubapp.core.ui.RepoDetailActions

private const val TAG = "ReadmeRender"

/**
 * 仓库详情页（T9 README 浏览 tracer bullet）。
 *
 * 顶部：仓库元数据（名称/描述/星/分叉/语言）
 * 下方：README 内容（FeatureDetector 判定：复杂 → WebView 服务端 HTML；普通 → 原生 MarkdownViewer）
 *
 * 链接分发（T9 验收第 3 条）：WebView bridge 与原生 MarkdownViewer 的链接统一经
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
        },
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
                            repo = state.repo,
                            readmeState = state.readmeState,
                            filesState = filesState,
                            filesViewModel = filesViewModel,
                            actions = actions,
                            onRetryReadme = { viewModel.retry() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoDetailContent(
    repo: Repository,
    readmeState: ReadmeState,
    filesState: RepoFilesUiState,
    filesViewModel: RepoFilesViewModel,
    actions: RepoDetailActions,
    onRetryReadme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        RepoHeader(repo = repo)

        Spacer(modifier = Modifier.height(16.dp))

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
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (tab) {
            0 -> {
                ReadmeSection(
                    readmeState = readmeState,
                    actions = actions,
                    onRetryReadme = onRetryReadme,
                    baseRepoUrl = buildRepoUrl(repo),
                )
            }

            else -> {
                FileTreeSection(
                    treeState = filesState.treeState,
                    defaultBranch = repo.defaultBranch,
                    viewModel = filesViewModel,
                )
            }
        }
    }
}

@Composable
private fun RepoHeader(repo: Repository) {
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
    }
}

@Composable
private fun ReadmeSection(
    readmeState: ReadmeState,
    actions: RepoDetailActions,
    onRetryReadme: () -> Unit,
    baseRepoUrl: String,
) {
    Text(
        text = stringResource(R.string.repo_readme_section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )

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
            when (readmeState.renderMode) {
                ReadmeRenderMode.WEBVIEW -> {
                    WebViewMarkdownRenderer(
                        sanitizedHtml = readmeState.content,
                        tokenProvider = { null },
                        bridgeCallback = createBridgeCallback(actions),
                        baseRepoUrl = baseRepoUrl,
                    )
                }

                ReadmeRenderMode.NATIVE -> {
                    // ADR-0007 原生增强主渲染（P1 #64）：否则 keep 基础版 MarkdownViewer 效果（标题大/徽章大/边距旧）
                    EnhancedMarkdownViewer(
                        markdown = readmeState.content,
                        onInternalLink = { parsed -> handleParsedUrl(parsed, actions) },
                        baseRepoUrl = baseRepoUrl,
                    )
                }
            }
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
