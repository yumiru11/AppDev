@file:Suppress("LongMethod") // 四 Tab 内容装配聚合展示逻辑，拆分反损可读性（IssueDetailScreen 同款先例）

package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFileStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.util.relativeTimeText

/** Conversation Tab：PR 正文（WebView）+ 时间线（评论/Review/行内评论/提交引用/事件） */
@Composable
internal fun ConversationTab(
    pullRequest: PullRequest,
    timeline: List<PullRequestTimelineItem>,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!pullRequest.body.isNullOrBlank()) {
            item(key = "body") {
                WebViewMarkdownRenderer(
                    sanitizedHtml = pullRequest.body,
                    tokenProvider = { null },
                    bridgeCallback = createPullRequestBridgeCallback(onInternalLink),
                    baseRepoUrl = baseRepoUrl,
                    // PR 无服务端 HTML API → 离线 GFM + 融合样式（WebView 内 markdown-it 渲染）
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (timeline.isEmpty()) {
            item(key = "timeline_empty") {
                Text(
                    text = stringResource(R.string.pull_request_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(items = timeline, key = { it.id.toString() }) { item ->
                when (item) {
                    is PullRequestTimelineItem.Comment -> {
                        CommentItem(
                            item = item,
                            onInternalLink = onInternalLink,
                            baseRepoUrl = baseRepoUrl,
                        )
                    }

                    is PullRequestTimelineItem.Review -> {
                        ReviewCard(item = item, onInternalLink = onInternalLink, baseRepoUrl = baseRepoUrl)
                    }

                    is PullRequestTimelineItem.ReviewComment -> {
                        ReviewCommentItem(
                            item = item,
                            onInternalLink = onInternalLink,
                            baseRepoUrl = baseRepoUrl,
                        )
                    }

                    is PullRequestTimelineItem.CommitReference -> {
                        CommitReferenceItem(item = item)
                    }

                    is PullRequestTimelineItem.Event -> {
                        EventItem(event = item)
                    }
                }
            }
        }
    }
}

/** WebView 正文 bridge：内部链接 → 应用内导航；外部链接 → 浏览器；纯锚点忽略。 */
@Suppress("EmptyFunctionBlock") // onCodeCopy/onImageClick/onCheckboxClick/onHeightChanged 为预留/T14 占位
@Composable
private fun createPullRequestBridgeCallback(onInternalLink: (ParsedUrl) -> Unit): MarkdownBridgeCallback {
    val context = LocalContext.current
    return object : MarkdownBridgeCallback {
        override fun onExternalLink(url: String) {
            // 纯锚点（#xxx）由页面自身处理，不拦截
            if (url.startsWith("#")) return
            openInBrowser(context, url)
        }

        override fun onInternalLink(parsed: ParsedUrl) {
            onInternalLink(parsed)
        }

        override fun onCodeCopy(code: String) {}

        override fun onImageClick(src: String) {}

        override fun onCheckboxClick(
            index: Int,
            checked: Boolean,
        ) {}

        override fun onHeightChanged(heightPx: Int) {}
    }
}

/** Commits Tab：提交列表（头像/缩写 SHA/消息/展开 diff 摘要） */
@Composable
internal fun CommitsTab(
    commits: List<PullRequestCommit>,
    expandedShas: Set<String>,
    onToggleExpanded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (commits.isEmpty()) {
        EmptyTabContent(
            text = stringResource(R.string.pull_request_commits_empty),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = commits, key = { it.sha }) { commit ->
            CommitRow(
                commit = commit,
                expanded = commit.sha in expandedShas,
                onToggleExpanded = { onToggleExpanded(commit.sha) },
            )
        }
    }
}

/** Checks Tab：CheckRun 列表（状态图标/结论/失败详情展开） */
@Composable
internal fun ChecksTab(
    checkRuns: List<CheckRun>,
    expandedIds: Set<Long>,
    onToggleExpanded: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (checkRuns.isEmpty()) {
        EmptyTabContent(
            text = stringResource(R.string.pull_request_checks_empty),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = checkRuns, key = { it.id }) { checkRun ->
            CheckRunRow(
                checkRun = checkRun,
                expanded = checkRun.id in expandedIds,
                onToggleExpanded = { onToggleExpanded(checkRun.id) },
            )
        }
    }
}

/** Files changed Tab：文件列表（+N −M，展开 patch） */
@Composable
internal fun FilesTab(
    files: List<PullRequestFile>,
    expandedNames: Set<String>,
    onToggleExpanded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) {
        EmptyTabContent(
            text = stringResource(R.string.pull_request_files_empty),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = files, key = { it.filename }) { file ->
            FileRow(
                file = file,
                expanded = file.filename in expandedNames,
                onToggleExpanded = { onToggleExpanded(file.filename) },
            )
        }
    }
}

/** 空态 Tab 内容（居中次要文本） */
@Composable
private fun EmptyTabContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 提交行：头像 + 缩写 SHA + 消息 + 相对时间；展开显示文件变更摘要 */
@Composable
private fun CommitRow(
    commit: PullRequestCommit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = commit.author?.avatarUrl,
                    contentDescription = commit.author?.login,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            commit.message
                                ?.lineSequence()
                                ?.firstOrNull()
                                .orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = commit.sha.take(SHORT_SHA_LENGTH),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        commit.createdAt?.let {
                            Text(
                                text = relativeTimeText(it) ?: it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (commit.files.isNotEmpty()) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription =
                                stringResource(
                                    if (expanded) R.string.pull_request_commit_collapse else R.string.pull_request_commit_expand,
                                ),
                        )
                    }
                }
            }
            if (expanded && commit.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                commit.files.forEach { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = file.filename.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pull_request_additions, file.additions),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.pull_request_deletions, file.deletions),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Check Run 行：状态图标 + 名称 + 结论；展开显示失败详情（output title/summary/text） */
@Composable
private fun CheckRunRow(
    checkRun: CheckRun,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = checkRunIcon(checkRun)
    val tint = checkRunTint(checkRun)
    val statusText = checkRunStatusText(checkRun)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = statusText,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = checkRun.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                        )
                        checkRun.appName?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (checkRun.hasExpandableOutput()) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription =
                                stringResource(
                                    if (expanded) R.string.pull_request_check_collapse else R.string.pull_request_check_expand,
                                ),
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                val title = checkRun.outputTitle
                val summary = checkRun.outputSummary
                val text = checkRun.outputText
                when {
                    title.isNullOrBlank() && summary.isNullOrBlank() && text.isNullOrBlank() -> {
                        Text(
                            text = stringResource(R.string.pull_request_check_no_output),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        title?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        summary?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        text?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 文件行：文件名 + 状态 + +N −M；展开显示 patch */
@Composable
private fun FileRow(
    file: PullRequestFile,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = fileStatusText(file.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pull_request_additions, file.additions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.pull_request_deletions, file.deletions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (!file.patch.isNullOrBlank()) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription =
                                stringResource(
                                    if (expanded) R.string.pull_request_file_collapse else R.string.pull_request_file_expand,
                                ),
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                val patch = file.patch
                if (patch.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.pull_request_file_no_patch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = patch,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Check Run → 状态图标（成功=CheckCircle/失败=Close/进行中=Refresh/排队与未知=Info） */
private fun checkRunIcon(checkRun: CheckRun): ImageVector =
    when {
        checkRun.status == CheckRunStatus.COMPLETED && checkRun.conclusion == CheckRunConclusion.SUCCESS -> {
            Icons.Filled.CheckCircle
        }

        checkRun.status == CheckRunStatus.COMPLETED && checkRun.conclusion == CheckRunConclusion.FAILURE -> {
            Icons.Filled.Close
        }

        checkRun.status == CheckRunStatus.IN_PROGRESS -> {
            Icons.Filled.Refresh
        }

        else -> {
            Icons.Filled.Info
        }
    }

/** Check Run → 语义色（成功=primary/失败=error/进行中=primary/其余=onSurfaceVariant） */
@Composable
private fun checkRunTint(checkRun: CheckRun): Color =
    when {
        checkRun.status == CheckRunStatus.COMPLETED && checkRun.conclusion == CheckRunConclusion.SUCCESS -> {
            MaterialTheme.colorScheme.primary
        }

        checkRun.status == CheckRunStatus.COMPLETED && checkRun.conclusion == CheckRunConclusion.FAILURE -> {
            MaterialTheme.colorScheme.error
        }

        checkRun.status == CheckRunStatus.IN_PROGRESS -> {
            MaterialTheme.colorScheme.primary
        }

        else -> {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

/** Check Run → 状态文案（status + conclusion 组合） */
@Composable
private fun checkRunStatusText(checkRun: CheckRun): String =
    when {
        checkRun.status == CheckRunStatus.COMPLETED && checkRun.conclusion != CheckRunConclusion.UNKNOWN -> {
            checkRunConclusionText(checkRun.conclusion)
        }

        else -> {
            checkRunStatusText(checkRun.status)
        }
    }

/** Check Run 状态 → 本地化文案 */
@Composable
private fun checkRunStatusText(status: CheckRunStatus): String =
    when (status) {
        CheckRunStatus.QUEUED -> stringResource(R.string.pull_request_check_status_queued)
        CheckRunStatus.IN_PROGRESS -> stringResource(R.string.pull_request_check_status_in_progress)
        CheckRunStatus.COMPLETED -> stringResource(R.string.pull_request_check_status_completed)
        CheckRunStatus.UNKNOWN -> stringResource(R.string.pull_request_check_status_unknown)
    }

/** Check Run 结论 → 本地化文案 */
@Composable
private fun checkRunConclusionText(conclusion: CheckRunConclusion): String =
    when (conclusion) {
        CheckRunConclusion.SUCCESS -> stringResource(R.string.pull_request_check_conclusion_success)
        CheckRunConclusion.FAILURE -> stringResource(R.string.pull_request_check_conclusion_failure)
        CheckRunConclusion.NEUTRAL -> stringResource(R.string.pull_request_check_conclusion_neutral)
        CheckRunConclusion.CANCELLED -> stringResource(R.string.pull_request_check_conclusion_cancelled)
        CheckRunConclusion.SKIPPED -> stringResource(R.string.pull_request_check_conclusion_skipped)
        CheckRunConclusion.TIMED_OUT -> stringResource(R.string.pull_request_check_conclusion_timed_out)
        CheckRunConclusion.ACTION_REQUIRED -> stringResource(R.string.pull_request_check_conclusion_action_required)
        CheckRunConclusion.UNKNOWN -> stringResource(R.string.pull_request_check_conclusion_unknown)
    }

/** 文件状态 → 本地化文案 */
@Composable
private fun fileStatusText(status: PullRequestFileStatus): String =
    when (status) {
        PullRequestFileStatus.ADDED -> stringResource(R.string.pull_request_file_status_added)
        PullRequestFileStatus.MODIFIED -> stringResource(R.string.pull_request_file_status_modified)
        PullRequestFileStatus.REMOVED -> stringResource(R.string.pull_request_file_status_removed)
        PullRequestFileStatus.RENAMED -> stringResource(R.string.pull_request_file_status_renamed)
        PullRequestFileStatus.COPIED -> stringResource(R.string.pull_request_file_status_copied)
        PullRequestFileStatus.CHANGED -> stringResource(R.string.pull_request_file_status_changed)
        PullRequestFileStatus.UNKNOWN -> stringResource(R.string.pull_request_file_status_unknown)
    }

/** 是否有可展开的输出（title/summary/text 任一非空） */
private fun CheckRun.hasExpandableOutput(): Boolean =
    !outputTitle.isNullOrBlank() || !outputSummary.isNullOrBlank() || !outputText.isNullOrBlank()

private const val SHORT_SHA_LENGTH = 7
