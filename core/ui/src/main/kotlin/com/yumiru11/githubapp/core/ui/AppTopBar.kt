package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.AppSearchBarButton
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface
import com.yumiru11.githubapp.core.designsystem.component.connectedShape
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings

/**
 * 应用顶栏（玻璃头，2026-10 UI 重构 = GitHubM3E 的 \`GhTopBar\` 版式）：
 * 左侧 56dp 胶囊搜索框（按钮语义 → 搜索页）+ 右侧两个 48dp **连接式**图标按钮
 * （通知铃铛带未读角标 / 账户头像），副行插槽 [sectionBar] 供首页分区条并入同一块玻璃。
 *
 * 与旧版差异：搜索框从 TopAppBar 的 title 槽换成独立 Row 里的胶囊；通知/账户从两个
 * 独立 IconButton 换成 3dp 细缝的连接组（外缘胶囊、内缘 8dp），与详情页动作条同一语言。
 * 回调驱动，不自己导航。
 *
 * 玻璃装配（ADR-0004 §6.1 允许清单）：[GlassSurface] 包住整块头：
 * - \`windowInsets = statusBars\`：玻璃背景延伸进状态栏区域（edge-to-edge 覆盖）
 * - [blurEnabled]：true（默认）API 31+ 真模糊 / 26–30 降级半透明；false 纯降级
 *
 * **玻璃矩形的尺寸决定 backdrop 能糊到什么**（issue #83）：分区条经 [sectionBar]
 * 进玻璃层后，Scaffold 交给内容区的 top inset 自动含副行高度，内容区据此把列表视口
 * 铺到玻璃背后。副行自身容器色须透明，让背后模糊透得上来。
 */
@Composable
fun AppTopBar(
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    unreadCount: Int = 0,
    // 玻璃开关按"顶栏"点位从 LocalGlassSettings 取（#167 / UI03）；显式传值只用于测试/预览
    blurEnabled: Boolean = LocalGlassSettings.current.enabledFor(GlassScope.TOP_BAR),
    modifier: Modifier = Modifier,
    /** 玻璃头副行插槽（首页分区条）；默认空 = 只有搜索行 */
    sectionBar: @Composable () -> Unit = {},
) {
    GlassSurface(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        scope = GlassScope.TOP_BAR,
        blurEnabled = blurEnabled,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.spacing.l,
                            end = AppDimens.spacing.m,
                            top = AppDimens.spacing.s,
                            bottom = AppDimens.spacing.s,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSearchBarButton(
                    placeholder = stringResource(R.string.search_hint),
                    onClick = onSearchClick,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AppDimens.spacing.s + 2.dp))
                // 连接式动作组（3dp 细缝 + 外缘胶囊 + 内缘 8dp），与仓库页 Star/Watch/Fork 同款
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.groupSeam)) {
                    TopBarActionButton(
                        icon = AppIcons.Notifications,
                        contentDescription = notificationBellDescription(unreadCount),
                        onClick = onNotificationClick,
                        shapeIndex = 0,
                        badge = if (unreadCount > 0) badgeCountText(unreadCount) else null,
                    )
                    TopBarActionButton(
                        icon = AppIcons.Person,
                        contentDescription = stringResource(R.string.nav_profile),
                        onClick = onProfileClick,
                        shapeIndex = 1,
                    )
                }
            }
            sectionBar()
        }
    }
}

/** 顶栏连接组里的单个 48dp 图标按钮（可选未读角标）。 */
@Composable
private fun TopBarActionButton(
    icon: com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec,
    contentDescription: String,
    onClick: () -> Unit,
    shapeIndex: Int,
    badge: String? = null,
) {
    val shape = connectedShape(shapeIndex, TOP_BAR_ACTION_COUNT, AppDimens.minTouchTarget)
    Box {
        Surface(
            modifier =
                Modifier
                    .size(AppDimens.minTouchTarget)
                    .clip(shape)
                    .clickable(onClick = onClick),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    spec = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 22.dp,
                )
            }
        }
        if (badge != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.error,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** 连接组里的按钮个数（通知 + 账户）。 */
private const val TOP_BAR_ACTION_COUNT = 2

/** 角标数字显示上限：超过显示 99+（audit 缺陷 #14 / issue #85）。 */
internal const val BADGE_MAX_COUNT = 99

/** 未读数是否溢出显示上限（>99 一律收敛为 99+）。纯函数，边界由 AppTopBarBadgeTest 锁定。 */
internal fun badgeCountOverflows(count: Int): Boolean = count > BADGE_MAX_COUNT

/**
 * 未读角标显示文本（issue #168 / UI27）：不超过上限时原样显示，
 * 超出用 i18n 模板 [R.string.notification_badge_overflow]（"%1$d+"）渲染上限值。
 */
@Composable
internal fun badgeCountText(count: Int): String =
    if (badgeCountOverflows(count)) {
        stringResource(R.string.notification_badge_overflow, BADGE_MAX_COUNT)
    } else {
        count.toString()
    }

/**
 * 铃铛语义描述：与角标显示同口径（issue #168 / UI27）——>99 时播报「99+ 条未读通知」，
 * 不与屏幕上看到的数字打架；0 未读时播报通用「通知」。
 */
@Composable
internal fun notificationBellDescription(unreadCount: Int): String =
    if (unreadCount <= 0) {
        stringResource(R.string.notification_title)
    } else if (badgeCountOverflows(unreadCount)) {
        pluralStringResource(
            R.plurals.notification_unread_badge_cd_overflow,
            BADGE_MAX_COUNT,
            BADGE_MAX_COUNT,
        )
    } else {
        pluralStringResource(R.plurals.notification_unread_badge_cd, unreadCount, unreadCount)
    }
