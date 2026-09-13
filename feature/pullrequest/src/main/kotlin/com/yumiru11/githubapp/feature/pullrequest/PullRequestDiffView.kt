package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.feature.pullrequest.data.DiffParser
import com.yumiru11.githubapp.feature.pullrequest.model.DiffLine
import com.yumiru11.githubapp.feature.pullrequest.model.DiffLineKind
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSideRow
import com.yumiru11.githubapp.feature.pullrequest.model.DiffViewMode
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread

/** 连续 context 行超过该阈值则折叠中间行（GitHub 网页同款做法） */
private const val CONTEXT_FOLD_THRESHOLD = 3

/**
 * side-by-side 的最低窗口宽度（UI-1 / M3 compact 上界 600dp）：
 *
 * - **< 600dp**（手机竖屏）：不提供 side-by-side —— 两栏各约 40 字符，长行再截断后
 *   完全不可读；只渲染 unified（横向滚动，见 [UnifiedDiff] 注释）。
 * - **≥ 600dp**（平板 / 桌面窗口）：保持原有双栏 + 切换按钮不变。
 *
 * 判定读窗口宽度 [android.content.res.Configuration.screenWidthDp]（与 core:markdown
 * 的宽度兜底同一机制；不引入 material3-window-size-class 新依赖）。
 */
private const val SIDE_BY_SIDE_MIN_WINDOW_WIDTH_DP = 600

/**
 * T16 自研轻量 diff 视图：unified / side-by-side 切换 + 行号 + 增删着色 + context 折叠 +
 * 行内评论锚点（点击有评论的行打开 [LineCommentSheet]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullRequestDiffView(
    path: String,
    diff: List<DiffLine>,
    comments: List<ReviewComment>,
    threads: List<ReviewThread>,
    onLineComment: (String, DiffSide, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (diff.isEmpty()) {
        Text(
            text = stringResource(R.string.pull_request_diff_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    var mode by rememberSaveable { mutableStateOf(DiffViewMode.UNIFIED) }
    // UI-1：窄窗口（< 600dp）不提供 side-by-side —— 隐藏切换按钮并强制 unified；
    // 宽窗口行为与此前完全一致（按钮 + 用户选择）。窗口从窄变宽时用户此前的选择仍保留。
    val sideBySideAvailable =
        LocalConfiguration.current.screenWidthDp >= SIDE_BY_SIDE_MIN_WINDOW_WIDTH_DP
    val activeMode = if (sideBySideAvailable) mode else DiffViewMode.UNIFIED
    Column(modifier = modifier.fillMaxWidth()) {
        if (sideBySideAvailable) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DiffViewMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = activeMode == entry,
                        onClick = { mode = entry },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = DiffViewMode.entries.size),
                        label = { Text(text = stringResource(entry.labelRes())) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.spacing.s))
        }
        Crossfade(targetState = activeMode, label = "diff-mode") { current ->
            when (current) {
                DiffViewMode.UNIFIED -> {
                    UnifiedDiff(
                        path = path,
                        diff = diff,
                        comments = comments,
                        threads = threads,
                        onLineComment = onLineComment,
                    )
                }

                DiffViewMode.SIDE_BY_SIDE -> {
                    SideBySideDiff(
                        path = path,
                        rows = DiffParser.toSideRows(diff),
                        comments = comments,
                        threads = threads,
                        onLineComment = onLineComment,
                    )
                }
            }
        }
    }
}

/** 折叠/展开中间 context 行的展示条目 */
private sealed interface DisplayItem {
    data class Row(
        val line: DiffLine,
    ) : DisplayItem

    /** 被折叠的连续 context 段（可点击展开） */
    data class Fold(
        val headIndex: Int,
        val count: Int,
    ) : DisplayItem
}

private fun unifiedItems(diff: List<DiffLine>): List<DisplayItem> {
    val items = ArrayList<DisplayItem>(diff.size)
    var i = 0
    while (i < diff.size) {
        val line = diff[i]
        if (line.kind == DiffLineKind.CONTEXT) {
            var runEnd = i
            while (runEnd + 1 < diff.size && diff[runEnd + 1].kind == DiffLineKind.CONTEXT) {
                runEnd++
            }
            val runLength = runEnd - i + 1
            if (runLength > CONTEXT_FOLD_THRESHOLD) {
                items += DisplayItem.Row(diff[i])
                items += DisplayItem.Fold(headIndex = i + 1, count = runLength - 2)
                items += DisplayItem.Row(diff[runEnd])
            } else {
                repeat(runLength) { index -> items += DisplayItem.Row(diff[i + index]) }
            }
            i = runEnd + 1
        } else {
            items += DisplayItem.Row(line)
            i++
        }
    }
    return items
}

@Composable
private fun UnifiedDiff(
    path: String,
    diff: List<DiffLine>,
    comments: List<ReviewComment>,
    threads: List<ReviewThread>,
    onLineComment: (String, DiffSide, Int) -> Unit,
) {
    val items = remember(diff) { unifiedItems(diff) }
    val expandedFolds = remember { mutableStateListOf<Int>() }
    val scrollState = rememberScrollState()
    // UI-1：unified 不再静默截断超长行 —— 整块 diff 横向滚动。列宽 = max(视口宽, 最长行
    // 的自然宽)，因此：① 长行完整可达（不再 maxLines=1 截断）；② 各行底色条纹等宽
    // （列宽固定，行 fillMaxWidth 拉伸）；③ 行号与正文同行，滚动时始终对齐。
    // 宽度用 `IntrinsicSize.Max` 取内容自然宽（不经过 scroll 节点，不引入测量 API）。
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(min = viewportWidth)
                        .width(IntrinsicSize.Max),
            ) {
                items.forEach { item ->
                    when (item) {
                        is DisplayItem.Row -> {
                            DiffLineRow(
                                path = path,
                                line = item.line,
                                comments = comments,
                                threads = threads,
                                onLineComment = onLineComment,
                            )
                        }

                        is DisplayItem.Fold -> {
                            val expanded = item.headIndex in expandedFolds
                            if (expanded) {
                                diff.subList(item.headIndex, item.headIndex + item.count).forEach { line ->
                                    DiffLineRow(
                                        path = path,
                                        line = line,
                                        comments = comments,
                                        threads = threads,
                                        onLineComment = onLineComment,
                                    )
                                }
                            } else {
                                FoldRow(
                                    count = item.count,
                                    onClick = { expandedFolds.add(item.headIndex) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBySideDiff(
    path: String,
    rows: List<DiffSideRow>,
    comments: List<ReviewComment>,
    threads: List<ReviewThread>,
    onLineComment: (String, DiffSide, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                SideBySideCell(
                    path = path,
                    line = row.old,
                    isOldSide = true,
                    comments = comments,
                    threads = threads,
                    onLineComment = onLineComment,
                    modifier = Modifier.weight(1f),
                )
                SideBySideCell(
                    path = path,
                    line = row.new,
                    isOldSide = false,
                    comments = comments,
                    threads = threads,
                    onLineComment = onLineComment,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DiffLineRow(
    path: String,
    line: DiffLine,
    comments: List<ReviewComment>,
    threads: List<ReviewThread>,
    onLineComment: (String, DiffSide, Int) -> Unit,
) {
    val anchor = line.anchor()
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(line.kind.background(scheme)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (anchor != null) Modifier.clickable { onLineComment(path, anchor.side, anchor.line) } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (line.kind) {
                DiffLineKind.HEADER,
                DiffLineKind.NO_NEWLINE,
                -> {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    LineNumberCell(number = line.oldNumber)
                    LineNumberCell(number = line.newNumber)
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
        if (anchor != null) {
            CommentMarker(
                count = commentsFor(path, anchor, comments).size,
                thread = threadFor(path, anchor, threads),
                onClick = { onLineComment(path, anchor.side, anchor.line) },
                modifier = Modifier.padding(start = 60.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun SideBySideCell(
    path: String,
    line: DiffLine?,
    isOldSide: Boolean,
    comments: List<ReviewComment>,
    threads: List<ReviewThread>,
    onLineComment: (String, DiffSide, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (line == null) {
        Spacer(modifier = modifier)
        return
    }
    when (line.kind) {
        DiffLineKind.HEADER,
        DiffLineKind.NO_NEWLINE,
        -> {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (isOldSide) line.text else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            return
        }

        else -> {
            Unit
        }
    }
    val anchor = line.anchor()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(line.kind.background(MaterialTheme.colorScheme)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (anchor != null) Modifier.clickable { onLineComment(path, anchor.side, anchor.line) } else Modifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineNumberCell(number = if (isOldSide) line.oldNumber else line.newNumber, width = 36.dp)
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
        if (anchor != null) {
            CommentMarker(
                count = commentsFor(path, anchor, comments).size,
                thread = threadFor(path, anchor, threads),
                onClick = { onLineComment(path, anchor.side, anchor.line) },
            )
        }
    }
}

@Composable
private fun LineNumberCell(
    number: Int?,
    width: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Text(
        text = number?.toString().orEmpty(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(width).padding(end = 8.dp),
    )
}

@Composable
private fun FoldRow(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.pull_request_diff_fold_expand, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CommentMarker(
    count: Int,
    thread: ReviewThread?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.pull_request_diff_comment_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (thread?.isResolved == true) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.pull_request_diff_thread_resolved),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 行内锚点（可评论行 = 仅变更行） */
private data class LineAnchor(
    val side: DiffSide,
    val line: Int,
)

private fun DiffLine.anchor(): LineAnchor? =
    when (kind) {
        DiffLineKind.ADDED -> newNumber?.let { LineAnchor(DiffSide.RIGHT, it) }
        DiffLineKind.REMOVED -> oldNumber?.let { LineAnchor(DiffSide.LEFT, it) }
        else -> null
    }

private fun commentsFor(
    path: String,
    anchor: LineAnchor,
    comments: List<ReviewComment>,
): List<ReviewComment> = comments.filter { it.path == path && it.side == anchor.side && it.anchorLine == anchor.line }

private fun threadFor(
    path: String,
    anchor: LineAnchor,
    threads: List<ReviewThread>,
): ReviewThread? = threads.firstOrNull { it.path == path && it.side == anchor.side && it.anchorLine == anchor.line }

private fun DiffLineKind.background(scheme: androidx.compose.material3.ColorScheme): Color =
    when (this) {
        DiffLineKind.ADDED -> scheme.primaryContainer.copy(alpha = 0.35f)

        DiffLineKind.REMOVED -> scheme.errorContainer.copy(alpha = 0.35f)

        DiffLineKind.CONTEXT -> scheme.surface

        DiffLineKind.HEADER,
        DiffLineKind.NO_NEWLINE,
        -> scheme.surfaceContainerHigh
    }

private fun DiffViewMode.labelRes(): Int =
    when (this) {
        DiffViewMode.UNIFIED -> R.string.pull_request_diff_unified
        DiffViewMode.SIDE_BY_SIDE -> R.string.pull_request_diff_side_by_side
    }
