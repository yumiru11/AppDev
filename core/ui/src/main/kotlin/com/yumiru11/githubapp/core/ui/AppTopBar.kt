package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface

/**
 * 应用顶栏（玻璃头）：左侧胶囊搜索框 + 右侧通知铃铛（未读角标）+ 头像，
 * 副行插槽 [sectionBar] 供首页小分区条并入同一块玻璃。
 *
 * 回调驱动，不自己导航。
 *
 * 玻璃装配（T6 Wave2，ADR-0004 §6.1 允许清单）：[GlassSurface] 包住 [TopAppBar]，
 * - `windowInsets = statusBars`：玻璃背景延伸进状态栏区域（edge-to-edge 覆盖），
 *   内容按状态栏内缩；TopAppBar 自身 insets 归零避免双重内缩
 * - `containerColor = Transparent`：透出玻璃层（半透明 surface @ AppBlur.SCRIM_ALPHA）
 * - [blurEnabled]：true（默认）API 31+ 真模糊 / 26–30 降级半透明；false 纯降级
 *   （设置页 T24 提供关闭项）
 *
 * **玻璃矩形的尺寸决定 backdrop 能糊到什么**（issue #83）：分区条经 [sectionBar]
 * 进玻璃层后，Scaffold 交给内容区的 top inset 自动含副行高度，内容区据此把列表视口
 * 铺到玻璃背后（见 HomeScreen）。分区条若留在内容列里，会把列表视口整体下推、
 * 滚动内容永远进不了顶栏矩形——即「玻璃只糊栏自身、看不出模糊」的几何根因。
 * 副行自身 containerColor 须透明，让背后模糊透得上来。
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
    /** 玻璃头副行插槽（首页小分区条）；默认空 = 只有 TopAppBar 一行 */
    sectionBar: @Composable () -> Unit = {},
) {
    GlassSurface(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        blurEnabled = blurEnabled,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    // audit 缺陷 #14（issue #85）：未读数并入铃铛语义描述，TalkBack 可感知数量
                    val notificationBellDescription =
                        if (unreadCount > 0) {
                            pluralStringResource(R.plurals.notification_unread_badge_cd, unreadCount, unreadCount)
                        } else {
                            stringResource(R.string.notification_title)
                        }
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge { Text(formatBadgeCount(unreadCount)) }
                            }
                        },
                    ) {
                        IconButton(onClick = onNotificationClick) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = notificationBellDescription,
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
            sectionBar()
        }
    }
}

/** 角标数字显示上限：超过显示 99+（audit 缺陷 #14 / issue #85）。 */
internal const val BADGE_MAX_COUNT = 99

/** 未读角标数字格式化：不超过 [BADGE_MAX_COUNT] 时原样显示，超出显示 99+。 */
internal fun formatBadgeCount(count: Int): String =
    if (count > BADGE_MAX_COUNT) {
        "${BADGE_MAX_COUNT}+"
    } else {
        count.toString()
    }
