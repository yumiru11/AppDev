package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.LocalMotionScale

/**
 * 骨架占位块：底色 + 横向扫过的高光带（首屏加载用，替代转圈）。
 *
 * 动效契约同 [AppWavyProgress]：动效强度 0 时高光带冻结在起始位置，静态但不空白。
 */
@Composable
fun AppShimmerSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppDimens.cornerMedium),
) {
    val motionScale = LocalMotionScale.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val animatedProgress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmerProgress",
    )
    val progress = if (motionScale > 0f) animatedProgress else -1f
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier = modifier.clip(shape)) {
        drawRect(color = base, size = size)
        val bandWidth = size.width * 0.4f
        val startX = progress * size.width - bandWidth / 2f
        drawRect(
            color = highlight.copy(alpha = 0.85f),
            topLeft = Offset(startX, 0f),
            size = Size(bandWidth, size.height),
        )
    }
}

/** 首屏骨架列表：等间距若干行占位块（列表页加载态统一形态）。 */
@Composable
fun AppSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    rowHeight: Dp = 88.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = AppDimens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.m),
    ) {
        repeat(rows) {
            AppShimmerSkeleton(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                shape = RoundedCornerShape(AppDimens.cornerMedium),
            )
        }
    }
}
