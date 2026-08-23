package com.yumiru11.githubapp.feature.notifications.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.notifications.NotificationsPanelUiState
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationGroup
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem

/**
 * 通知面板 @Preview 族（audit 缺陷 #16）：行与面板内容 Light/Dark 双主题预览，
 * 样例数据离线自足；独立成文件避免主装配文件 detekt TooManyFunctions 超标。
 */

private fun previewItem(
    id: String,
    repo: String,
    title: String,
    type: String,
    reason: String,
    unread: Boolean,
    updatedAt: String,
): NotificationItem =
    NotificationItem(
        id = id,
        repoFullName = repo,
        subjectTitle = title,
        subjectType = type,
        reason = reason,
        unread = unread,
        updatedAt = updatedAt,
        htmlUrl = "https://github.com/$repo/issues/1347",
    )

private fun previewGroups(): List<NotificationGroup> =
    listOf(
        NotificationGroup(
            repoFullName = "yumiru11/AppDev",
            items =
                listOf(
                    previewItem(
                        "1",
                        "yumiru11/AppDev",
                        "feat(notifications): 通知面板完整形态",
                        "PullRequest",
                        "mention",
                        true,
                        "2026-08-21T09:00:00Z",
                    ),
                    previewItem(
                        "2",
                        "yumiru11/AppDev",
                        "fix(designsystem): backdrop blur 修复",
                        "Issue",
                        "subscribed",
                        false,
                        "2026-08-21T08:00:00Z",
                    ),
                ),
        ),
        NotificationGroup(
            repoFullName = "rikkahub/rikkahub",
            items =
                listOf(
                    previewItem("3", "rikkahub/rikkahub", "Release v1.4.0", "Release", "ci_activity", true, "2026-08-20T18:03:00Z"),
                ),
        ),
    )

@Preview(name = "Light", showBackground = true)
@Composable
private fun NotificationCardPreviewLight() {
    AppTheme(darkTheme = false) {
        NotificationCard(item = previewGroups()[0].items[0], onClick = {})
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun NotificationCardPreviewDark() {
    AppTheme(darkTheme = true) {
        NotificationCard(item = previewGroups()[0].items[0], onClick = {})
    }
}

@Preview(name = "Light")
@Composable
private fun NotificationsPanelContentPreviewLight() {
    AppTheme(darkTheme = false) {
        NotificationsPanelContent(
            uiState = NotificationsPanelUiState.Success(filter = NotificationFilter.ALL, groups = previewGroups()),
            filter = NotificationFilter.ALL,
            onDismiss = {},
            onMarkAllRead = {},
            onSelectFilter = {},
            onToggleGroup = {},
            onMarkRead = {},
            onDelete = {},
            onRetry = {},
            onLoginClick = {},
            onNotificationClick = {},
        )
    }
}

@Preview(name = "Dark")
@Composable
private fun NotificationsPanelContentPreviewDark() {
    AppTheme(darkTheme = true) {
        NotificationsPanelContent(
            uiState = NotificationsPanelUiState.Success(filter = NotificationFilter.ALL, groups = previewGroups()),
            filter = NotificationFilter.ALL,
            onDismiss = {},
            onMarkAllRead = {},
            onSelectFilter = {},
            onToggleGroup = {},
            onMarkRead = {},
            onDelete = {},
            onRetry = {},
            onLoginClick = {},
            onNotificationClick = {},
        )
    }
}
