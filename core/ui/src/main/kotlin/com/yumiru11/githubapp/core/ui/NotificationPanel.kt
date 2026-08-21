package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons

/**
 * 通知面板内容（无动画包装，可供截图测试直接使用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPanelContent(
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.notification_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row {
                    TextButton(onClick = onMarkAllRead) {
                        Text(stringResource(R.string.notification_mark_all_read))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.notification_close),
                        )
                    }
                }
            }
            HorizontalDivider()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // #84：共享空态组件替换手搓 Text（audit §3.2「硬编码空态」）
                AppEmptyState(
                    icon = AppDevOcticons.Check,
                    title = stringResource(R.string.notification_empty),
                )
            }
        }
    }
}

/**
 * 通知面板：全屏滑入弹出面板（AnimatedVisibility + slideInVertically/slideOutVertically）。
 *
 * 不进底部导航，由顶栏通知铃铛触发。
 */
@Composable
fun NotificationPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.fillMaxSize(),
    ) {
        NotificationPanelContent(
            onDismiss = onDismiss,
            onMarkAllRead = onMarkAllRead,
        )
    }
}
