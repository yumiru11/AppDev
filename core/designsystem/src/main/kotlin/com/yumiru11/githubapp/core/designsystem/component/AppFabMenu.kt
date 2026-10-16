package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import kotlinx.coroutines.delay

/**
 * 可展开的浮动操作按钮菜单（基准 = M3E \`GhFabMenu\`，需求：功能多的场景用 FAB 菜单）。
 *
 * - 主轴图标展开时旋转 135°（[AppMotion.scaledDuration] 折算，动效 0 时即时）；
 * - 次项自下而上展开（标签胶囊 + SmallFAB），点击后自动收起；
 * - [hideOnScroll] 为 true 时：列表滑动中整组**收起并淡出**，停止滑动 300ms 后恢复
 *   （见 [rememberScrollAwareFabVisible]）。
 *
 * @param expanded 展开态（由调用方持有，便于在入口处做互斥）
 * @param onExpandedChange 展开态变化回调
 * @param mainIcon 主轴图标（通常是一个「+」）
 * @param mainContentDescription 主轴无障碍描述（i18n，由调用方传入）
 * @param items 次项（自上而下渲染，视觉上自下而上展开）
 * @param isScrollInProgress 当前列表是否在滑动（驱动自动收起）
 */
@Composable
fun AppFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    mainIcon: ImageVector,
    mainContentDescription: String,
    items: List<AppFabMenuItem>,
    modifier: Modifier = Modifier,
    isScrollInProgress: Boolean = false,
) {
    val visible = rememberScrollAwareFabVisible(isScrollInProgress)
    // 滑动开始：立即收起已展开的菜单（需求：滑动时自动收起）
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress && expanded) onExpandedChange(false)
    }
    val rotation by
        animateFloatAsState(
            targetValue = if (expanded) 135f else 0f,
            animationSpec = tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE)),
            label = "fabRotation",
        )
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))) +
                expandVertically(tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))),
        exit =
            fadeOut(tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))) +
                shrinkVertically(tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))),
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(AppDimens.fabGap),
        ) {
            items.forEach { item ->
                AnimatedVisibility(
                    visible = expanded,
                    enter =
                        fadeIn(tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))) +
                            expandVertically(),
                    exit =
                        fadeOut(tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE))) +
                            shrinkVertically(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(AppDimens.cornerSmall),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier =
                                    Modifier.padding(
                                        horizontal = AppDimens.spacing.m,
                                        vertical = AppDimens.spacing.xs + 2.dp,
                                    ),
                            )
                        }
                        Spacer(Modifier.width(AppDimens.fabGap))
                        SmallFloatingActionButton(
                            onClick = {
                                onExpandedChange(false)
                                item.onClick()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            AppIconButton(
                icon = mainIcon,
                contentDescription = mainContentDescription,
                onClick = { onExpandedChange(!expanded) },
                filled = true,
                size = 56.dp,
                iconSize = 26.dp,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

/** 停止滑动后恢复 FAB 的等待时长（需求：停止滑动 300ms 后再展开）。 */
const val FAB_REVEAL_DELAY_MILLIS: Int = 300

/**
 * 滑动感知的 FAB 可见性：滑动中隐藏，停止滑动 [FAB_REVEAL_DELAY_MILLIS] 后恢复。
 *
 * 纯语义（供单测锁定）：[fabVisibleAfterIdleMillis] 是判定内核，
 * 本 composable 只负责把 [isScrollInProgress] 的变化喂给它。
 * 动效强度为 0 时不进入隐藏态（避免"闪一下"的静态抖动，也让移除动画的用户
 * 在任何时候都能点到 FAB）。
 */
@Composable
fun rememberScrollAwareFabVisible(isScrollInProgress: Boolean): Boolean {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            visible = false
        } else {
            delay(FAB_REVEAL_DELAY_MILLIS.toLong())
            visible = true
        }
    }
    return visible
}

/**
 * 判定内核：滑动停止后经过 [elapsedMillis] 是否应重新显示。
 *
 * @param elapsedMillis 距停止滑动的毫秒数
 * @param revealDelayMillis 阈值；默认 [FAB_REVEAL_DELAY_MILLIS]
 */
fun fabVisibleAfterIdleMillis(
    elapsedMillis: Long,
    revealDelayMillis: Int = FAB_REVEAL_DELAY_MILLIS,
): Boolean = elapsedMillis >= revealDelayMillis
