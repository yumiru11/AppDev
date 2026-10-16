package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFileStatus

// 变更类型 → 语义容器色 / 前景色。
//
// 单独成文件：PullRequestTabContent.kt 已贴 detekt 的 TooManyFunctions 上限（20），
// 这两个映射是纯展示逻辑，拆出来既降函数数，也让「状态色」有唯一事实源。

/** 变更类型的容器色（新增/删除/修改分别取语义容器色，保证深浅主题都可读）。 */
@Composable
internal fun fileStatusContainer(status: PullRequestFileStatus): Color =
    when (status) {
        PullRequestFileStatus.ADDED -> MaterialTheme.colorScheme.primaryContainer

        PullRequestFileStatus.REMOVED -> MaterialTheme.colorScheme.errorContainer

        PullRequestFileStatus.MODIFIED,
        PullRequestFileStatus.CHANGED,
        -> MaterialTheme.colorScheme.secondaryContainer

        PullRequestFileStatus.RENAMED,
        PullRequestFileStatus.COPIED,
        -> MaterialTheme.colorScheme.tertiaryContainer

        PullRequestFileStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

/** 变更类型的前景色（与 [fileStatusContainer] 配对）。 */
@Composable
internal fun fileStatusContent(status: PullRequestFileStatus): Color =
    when (status) {
        PullRequestFileStatus.ADDED -> MaterialTheme.colorScheme.onPrimaryContainer

        PullRequestFileStatus.REMOVED -> MaterialTheme.colorScheme.onErrorContainer

        PullRequestFileStatus.MODIFIED,
        PullRequestFileStatus.CHANGED,
        -> MaterialTheme.colorScheme.onSecondaryContainer

        PullRequestFileStatus.RENAMED,
        PullRequestFileStatus.COPIED,
        -> MaterialTheme.colorScheme.onTertiaryContainer

        PullRequestFileStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
