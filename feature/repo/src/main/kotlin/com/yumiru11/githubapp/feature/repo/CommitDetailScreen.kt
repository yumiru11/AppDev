@file:Suppress("TooManyFunctions", "LongMethod")
// 屏幕文件：多个小 Composable 是 Compose 惯用结构（RepoDetailScreen 同款先例）

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.yumiru11.githubapp.core.ui.AppSnackbarHost
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import kotlinx.coroutines.launch

/**
 * COMMIT 详情页（L09，替换此前的 PlaceholderSearchScreen 占位）。
 *
 * 头部：短 SHA（7 位，点击复制完整 SHA）+ 作者头像/login + 相对时间 + 提交信息 + `+N −M` 统计；
 * 文件变更列表 → 点击进该文件 unified diff（页内切换，不进导航）。
 *
 * diff 解析走 feature 内 [CommitDiffParser]（Konsist 禁止 feature 间 import
 * feature:pullrequest 的 DiffParser），取舍见 PR 说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CommitDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.repo_commit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.repo_commit_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is CommitDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is CommitDetailUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is CommitDetailUiState.Success -> {
                    val selected = state.selectedFile
                    if (selected != null) {
                        CommitDiffSection(
                            file = selected,
                            lines = state.diffLines,
                            onBack = { viewModel.clearSelection() },
                        )
                    } else {
                        CommitSection(
                            commit = state.commit,
                            onSelectFile = { viewModel.selectFile(it) },
                            onCopySha = {
                                copyToClipboard(context, state.commit.sha, CLIP_LABEL_COMMIT_SHA)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.repo_commit_sha_copied))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 提交头 + 文件变更列表。 */
@Composable
private fun CommitSection(
    commit: CommitDetail,
    onSelectFile: (CommitFile) -> Unit,
    onCopySha: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = COMMIT_HEADER_KEY) {
            CommitHeader(commit = commit, onCopySha = onCopySha)
        }
        item(key = COMMIT_FILES_TITLE_KEY) {
            Text(
                text = stringResource(R.string.repo_commit_files),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (commit.files.isEmpty()) {
            item(key = COMMIT_FILES_EMPTY_KEY) {
                EmptyHint(text = stringResource(R.string.repo_commit_files_empty))
            }
        } else {
            items(commit.files, key = { it.filename }) { file ->
                CommitFileRow(file = file, onClick = { onSelectFile(file) })
            }
        }
    }
}

/** 提交头：短 SHA + 复制、作者、相对时间、提交信息、统计。 */
@Composable
private fun CommitHeader(
    commit: CommitDetail,
    onCopySha: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = commit.shortSha,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                IconButton(onClick = onCopySha) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Content_copy,
                        contentDescription = stringResource(R.string.repo_commit_copy_sha),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                CommitStats(additions = commit.additions, deletions = commit.deletions)
            }

            Spacer(modifier = Modifier.height(8.dp))

            commit.authorDate?.let { date ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    commit.authorAvatarUrl?.let { avatar ->
                        AsyncImage(
                            model = avatar,
                            contentDescription = stringResource(R.string.repo_commit_author_avatar),
                            modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = commit.authorLogin ?: commit.authorName.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = relativeTimeText(date) ?: date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            CommitMessage(message = commit.message)
        }
    }
}

/** 提交信息：首行为标题（titleMedium），其余为正文（bodyMedium）。 */
@Composable
private fun CommitMessage(message: String) {
    val lines = message.split("\n")
    val subject = lines.firstOrNull().orEmpty()
    val body = lines.drop(1).joinToString("\n").trim()
    Column {
        Text(
            text = subject,
            style = MaterialTheme.typography.titleMedium,
        )
        if (body.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 增删统计（+N −M；沿用 diff 的 primaryContainer/errorContainer 语义色，零硬编码颜色）。 */
@Composable
private fun CommitStats(
    additions: Int,
    deletions: Int,
) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.repo_commit_additions, additions),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
        Text(
            text = stringResource(R.string.repo_commit_deletions, deletions),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.error,
        )
    }
}

/** 单个变更文件行：状态徽章 + 路径 + 增删数。 */
@Composable
private fun CommitFileRow(
    file: CommitFile,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommitStatusBadge(status = file.status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = file.filename,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            CommitStats(additions = file.additions, deletions = file.deletions)
        }
    }
}

/** 文件状态徽章（新增/删除/修改/重命名）。 */
@Composable
private fun CommitStatusBadge(status: CommitFileStatus) {
    Box(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(commitStatusRes(status)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 文件 diff 视图（选中文件）：返回行 + 行号 + unified 着色。 */
@Composable
private fun CommitDiffSection(
    file: CommitFile,
    lines: List<CommitDiffLine>,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.repo_commit_back_to_files),
                )
            }
            Text(
                text = file.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                EmptyHint(text = stringResource(R.string.repo_commit_diff_empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    CommitDiffRow(line = line)
                }
            }
        }
    }
}

/** diff 单行（行号 + 类型着色；HEADER/NO_NEWLINE 无行号）。 */
@Composable
private fun CommitDiffRow(line: CommitDiffLine) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(diffBackground(line.kind, scheme))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (line.kind == CommitDiffLineKind.ADDED ||
            line.kind == CommitDiffLineKind.REMOVED ||
            line.kind == CommitDiffLineKind.CONTEXT
        ) {
            DiffNumberCell(number = line.oldNumber)
            DiffNumberCell(number = line.newNumber)
        }
        Text(
            text = line.text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiffNumberCell(number: Int?) {
    Text(
        text = number?.toString().orEmpty(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(36.dp).padding(end = 8.dp),
    )
}

/** diff 行背景（与 PullRequestDiffView 同款语义色，零硬编码颜色）。 */
private fun diffBackground(
    kind: CommitDiffLineKind,
    scheme: androidx.compose.material3.ColorScheme,
): Color =
    when (kind) {
        CommitDiffLineKind.ADDED -> scheme.primaryContainer.copy(alpha = 0.35f)
        CommitDiffLineKind.REMOVED -> scheme.errorContainer.copy(alpha = 0.35f)
        CommitDiffLineKind.CONTEXT -> scheme.surface
        CommitDiffLineKind.HEADER, CommitDiffLineKind.NO_NEWLINE -> scheme.surfaceContainerHigh
    }

/** 文件状态 → 文案资源（ViewModel 只传类型，不产文案）。 */
private fun commitStatusRes(status: CommitFileStatus): Int =
    when (status) {
        CommitFileStatus.ADDED -> R.string.repo_commit_status_added
        CommitFileStatus.REMOVED -> R.string.repo_commit_status_removed
        CommitFileStatus.MODIFIED -> R.string.repo_commit_status_modified
        CommitFileStatus.RENAMED -> R.string.repo_commit_status_renamed
        CommitFileStatus.UNKNOWN -> R.string.repo_commit_status_unknown
    }

/** LazyColumn 固定 item key（避免与文件路径 key 冲突） */
private const val COMMIT_HEADER_KEY = "commit-header"
private const val COMMIT_FILES_TITLE_KEY = "commit-files-title"
private const val COMMIT_FILES_EMPTY_KEY = "commit-files-empty"

/** 剪贴板标签（非 UI 文案，仅系统剪贴板条目名） */
private const val CLIP_LABEL_COMMIT_SHA = "commit-sha"
