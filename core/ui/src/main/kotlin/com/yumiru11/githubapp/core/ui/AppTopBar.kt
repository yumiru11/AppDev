package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface

/**
 * 应用顶栏：左侧胶囊搜索框 + 右侧通知铃铛（未读角标）+ 头像。
 *
 * 回调驱动，不自己导航。
 *
 * 玻璃装配（T6 Wave2，ADR-0004 §6.1 允许清单）：[GlassSurface] 包住 [TopAppBar]，
 * - `windowInsets = statusBars`：玻璃背景延伸进状态栏区域（edge-to-edge 覆盖），
 *   内容按状态栏内缩；TopAppBar 自身 insets 归零避免双重内缩
 * - `containerColor = Transparent`：透出玻璃层（半透明 surface @ AppBlur.SCRIM_ALPHA）
 * - [blurEnabled]：true（默认）API 31+ 真模糊 / 26–30 降级半透明；false 纯降级
 *   （设置页 T24 提供关闭项）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    unreadCount: Int = 0,
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        blurEnabled = blurEnabled,
    ) {
        TopAppBar(
            title = {
                Surface(
                    onClick = onSearchClick,
                    // 胶囊形（ui-design §1.1-5 圆角全覆盖）：percent 50 = 高度一半，替代散落的 24dp 硬编码
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            actions = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge { Text("$unreadCount") }
                        }
                    },
                ) {
                    IconButton(onClick = onNotificationClick) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.notification_title),
                        )
                    }
                }
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.nav_profile),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0.dp),
        )
    }
}
