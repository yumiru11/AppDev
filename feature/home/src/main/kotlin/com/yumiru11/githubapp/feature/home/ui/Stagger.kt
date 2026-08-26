package com.yumiru11.githubapp.feature.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/** 参与首屏 stagger 的最大行数（#89）：其余行直出，避免深页滚动时反复入场。 */
internal const val STAGGER_MAX_ITEMS = 12

/**
 * 第 [index] 行的 stagger 延迟（#89，ui-design.md §4.2 H2-5：24ms/项）。
 * 超出首屏窗口或非法索引一律 0（立即开始）。纯函数，单测覆盖。
 */
internal fun staggerDelayMillis(index: Int): Int =
    if (index in 0 until STAGGER_MAX_ITEMS) index * AppMotion.LIST_STAGGER_INTERVAL_MILLIS else 0

/**
 * 列表项进入动效（slide-up + fade，ui-design.md §3.1「列表首项进入 slide+fade」）：
 * - 间隔 [staggerDelayMillis]，时长 [AppMotion.DURATION_LIST_ITEM] 经
 *   [AppMotion.scaledDuration] 缩放（设置动画强度 × 系统动画缩放取 min；0 = 立即完成）
 * - rememberSaveable 记账：已播过的行滚动回收后再组合不重播
 */
@Composable
internal fun rememberStaggerEnterModifier(index: Int): Modifier {
    val shown = rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown.value) 1f else 0f) }
    val durationMillis = AppMotion.scaledDuration(AppMotion.DURATION_LIST_ITEM)
    val slideDistancePx = with(LocalDensity.current) { SLIDE_DISTANCE.toPx() }
    LaunchedEffect(durationMillis) {
        if (shown.value || durationMillis <= 0) {
            shown.value = true
            return@LaunchedEffect
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis = durationMillis,
                    delayMillis = staggerDelayMillis(index),
                    easing = AppMotion.EmphasizedDecelerate,
                ),
        )
        shown.value = true
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * slideDistancePx
    }
}

private val SLIDE_DISTANCE = 24.dp
