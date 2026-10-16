package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/** 分区条的一项（M3E \`GhTab\`）。 */
data class AppTab(
    val label: String,
    val icon: AppIconSpec? = null,
    val badge: String? = null,
)

/**
 * M3 Expressive 可滚动分区条（基准 = M3E \`GhScrollableTabs\`）。
 *
 * 与 [androidx.compose.material3.PrimaryScrollableTabRow] 的差异（用户拍板的观感口径）：
 * - 指示条**跟着标签宽度**（短标签不显得空荡），选中项高 3dp、宽 28dp，圆角顶边；
 * - 选中项自动滚入视口（[LazyRow] + [LaunchedEffect]，分区多时不丢选中态）；
 * - 图标随选中态切换实心/空心（[AppIcon] 的 \`selected\`）。
 *
 * 无障碍：每个标签是独立可点项，语义由文本承载；图标为装饰（contentDescription = null）。
 * 动效：颜色/指示条动画时长经 [AppMotion.scaledDuration]（动效强度 0 时即时完成）。
 */
@Composable
fun AppScrollableTabs(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = AppDimens.spacing.s),
    showDivider: Boolean = true,
) {
    val listState = rememberLazyListState()
    val colorSpec = tween<androidx.compose.ui.graphics.Color>(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))
    val indicatorSpec = tween<androidx.compose.ui.unit.Dp>(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))

    // 选中项自动滚入视口： swipe 翻页时条要跟得上（首页/仓库页分区多）
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) {
            runCatching { listState.animateScrollToItem(selectedIndex) }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs) { index, tab ->
                AppTabItem(
                    tab = tab,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    colorSpec = colorSpec,
                    indicatorSpec = indicatorSpec,
                )
            }
        }
        if (showDivider) {
            AppHairline(inset = 0.dp)
        }
    }
}

/**
 * 单个分区项：图标（选中实心）+ 标签 + 可选角标 + 指示条。
 *
 * 抽成独立 composable 的原因：分区条主体已含 LazyRow/自动滚动/分隔线三件事，
 * 内联逐项渲染会超过 detekt 的 LongMethod 阈值（且这里是最可能需要单独调的地方）。
 */
@Composable
private fun AppTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    colorSpec: AnimationSpec<Color>,
    indicatorSpec: AnimationSpec<Dp>,
) {
    val color by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = colorSpec,
            label = "tabColor",
        )
    val indicatorWidth by
        animateDpAsState(
            targetValue = if (selected) 28.dp else 0.dp,
            animationSpec = indicatorSpec,
            label = "tabIndicator",
        )
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(AppDimens.cornerSmall))
                .clickable(onClick = onClick)
                .padding(horizontal = AppDimens.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.icon != null) {
                AppIcon(
                    spec = tab.icon,
                    contentDescription = null,
                    selected = selected,
                    tint = color,
                    size = 18.dp,
                )
                Spacer(Modifier.width(AppDimens.spacing.xs + 2.dp))
            }
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tab.badge != null) {
                Spacer(Modifier.width(AppDimens.spacing.xs + 2.dp))
                Badge2(text = tab.badge)
            }
        }
        Box(
            modifier =
                Modifier
                    .width(indicatorWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 未读/计数角标（分区条上的小胶囊）。 */
@Composable
private fun Badge2(text: String) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.error,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            modifier = Modifier.padding(horizontal = AppDimens.spacing.xs + 2.dp, vertical = 1.dp),
        )
    }
}
