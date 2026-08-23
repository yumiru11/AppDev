package com.yumiru11.githubapp.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import com.yumiru11.githubapp.feature.notifications.NotificationsPanelUiState
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationGroup
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NotificationsPanelContent 截图基准测试（light / dark 两态，#88）。
 *
 * 测试无动画包装的 [NotificationsPanelContent]（分组列表完整形态）。
 * 面板本体为半透明玻璃（GlassSurface）：基准须垫实体 surface 底，
 * 否则 PNG 背景透明、verify 观感失真。
 * 基准 PNG：feature/notifications/src/test/screenshots/NotificationsPanel_{light,dark}.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NotificationsPanelScreenshotTest : ScreenshotTest() {
    private fun item(
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
            htmlUrl = "https://github.com/" + repo + "/issues/1347",
        )

    private fun sampleState(): NotificationsPanelUiState.Success =
        NotificationsPanelUiState.Success(
            filter = NotificationFilter.ALL,
            groups =
                listOf(
                    NotificationGroup(
                        repoFullName = "yumiru11/AppDev",
                        items =
                            listOf(
                                item(
                                    "1",
                                    "yumiru11/AppDev",
                                    "feat(notifications): 通知面板完整形态",
                                    "PullRequest",
                                    "mention",
                                    true,
                                    "2026-08-21T09:00:00Z",
                                ),
                                item(
                                    "2",
                                    "yumiru11/AppDev",
                                    "fix(designsystem): 毛玻璃改为 backdrop 模糊实现",
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
                                item(
                                    "3",
                                    "rikkahub/rikkahub",
                                    "Release v1.4.0",
                                    "Release",
                                    "ci_activity",
                                    true,
                                    "2026-08-20T18:03:00Z",
                                ),
                            ),
                    ),
                ),
        )

    /** 基准内容：实体 surface 底（随基类 colorScheme 自动切明暗）+ 面板完整形态 */
    @Composable
    private fun SamplePanel() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            NotificationsPanelContent(
                uiState = sampleState(),
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

    @Test
    fun notificationsPanel_lightTheme_matchesBaseline() {
        captureScreenshot(name = "NotificationsPanel_light", darkTheme = false) {
            SamplePanel()
        }
    }

    @Test
    fun notificationsPanel_darkTheme_matchesBaseline() {
        captureScreenshot(name = "NotificationsPanel_dark", darkTheme = true) {
            SamplePanel()
        }
    }
}
