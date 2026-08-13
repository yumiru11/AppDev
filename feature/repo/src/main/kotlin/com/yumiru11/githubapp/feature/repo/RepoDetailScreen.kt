package com.yumiru11.githubapp.feature.repo

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * 仓库详情页（T9 README 浏览 tracer bullet）。
 *
 * 顶部：仓库元数据（名称/描述/星/分叉/语言）
 * 下方：README 内容（服务端 HTML → WebView 兜底通道；原生 Markdown → MarkdownViewer）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit = {},
    viewModel: RepoDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is RepoDetailUiState.Success -> {
                    RepoDetailContent(
                        repo = state.repo,
                        readmeState = state.readmeState,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoDetailContent(
    repo: Repository,
    readmeState: ReadmeState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        RepoHeader(repo = repo)

        Spacer(modifier = Modifier.height(16.dp))

        ReadmeSection(readmeState = readmeState)
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
private fun ReadmeSection(readmeState: ReadmeState) {
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
            when (readmeState.renderMode) {
                ReadmeRenderMode.WEBVIEW -> {
                    WebViewMarkdownRenderer(
                        sanitizedHtml = readmeState.html,
                        tokenProvider = { null },
                        bridgeCallback = createBridgeCallback(),
                    )
                }

                ReadmeRenderMode.NATIVE -> {
                    MarkdownViewer(
                        markdown = readmeState.html,
                        onInternalLink = { /* T14 深链导航 */ },
                    )
                }
            }
        }

        is ReadmeState.Error -> {
            ErrorContent(
                message = readmeState.message,
                onRetry = {},
            )
        }
    }
}

/**
 * 默认 bridge callback：link/copy/image/checkbox/height 全空实现（T14 接线真实导航）。
 */
@Suppress("EmptyFunctionBlock") // T14 接线真实导航前为占位桩
@Composable
private fun createBridgeCallback(): MarkdownBridgeCallback =
    object : MarkdownBridgeCallback {
        override fun onExternalLink(url: String) {}

        override fun onInternalLink(parsed: ParsedUrl) {}

        override fun onCodeCopy(code: String) {}

        override fun onImageClick(src: String) {}

        override fun onCheckboxClick(
            index: Int,
            checked: Boolean,
        ) {}

        override fun onHeightChanged(heightPx: Int) {}
    }

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
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
