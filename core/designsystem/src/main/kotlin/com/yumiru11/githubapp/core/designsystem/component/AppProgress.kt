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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.LocalMotionScale
import kotlin.math.PI
import kotlin.math.sin

/**
 * M3 Expressive 波形不确定进度（基准 = M3E \`GhWavyProgress\`）：三条正弦波横向行进。
 *
 * 动效强度为 0（系统「移除动画」/设置页滑杆归零）时**静态渲染**（相位固定），
 * 与项目既有的「动效 0 = 静态」契约一致——不允许无限动画在 0 档继续跑。
 */
@Composable
fun AppWavyProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
) {
    val animated = LocalMotionScale.current > 0f
    val transition = rememberInfiniteTransition(label = "wavy")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (animated) 1f else 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "phase",
        )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val amplitude = h / 6f
        val wavelength = (w / 1.6f).coerceAtLeast(1f)
        repeat(3) { index ->
            val offsetY = h / 2f + (index - 1) * (h / 3.2f)
            val alpha = 1f - index * 0.28f
            val path = Path()
            var x = 0f
            while (x <= w) {
                val y =
                    offsetY +
                        amplitude *
                        sin((x / wavelength + phase + index * 0.12f) * 2f * PI).toFloat()
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 4f
            }
            drawPath(path = path, color = color.copy(alpha = alpha), style = Stroke(width = strokeWidth.toPx()))
        }
    }
}

/**
 * 骨架块（shimmer）：底色取 ExtendedColors.skeleton，行进高光为容器亮一档。
 *
 * 动效强度 0 时只画底色（静态骨架），不续订帧。
 */
@Composable
fun AppShimmer(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AppDimens.cornerSmall),
) {
    val animated = LocalMotionScale.current > 0f
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by
        transition.animateFloat(
            initialValue = -1f,
            targetValue = if (animated) 2f else -1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerProgress",
        )
    val base = MaterialTheme.extendedColors.skeleton
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier = modifier.clip(shape)) {
        drawRect(color = base, size = size)
        if (!animated) return@Canvas
        val bandWidth = size.width * 0.4f
        val startX = progress * size.width - bandWidth / 2f
        drawRect(
            color = highlight.copy(alpha = 0.85f),
            topLeft = Offset(startX, 0f),
            size = Size(bandWidth, size.height),
        )
    }
}

/** 首屏骨架列表（基准 = M3E \`GhListSkeleton\`）：16dp 外边距、10dp 缝、84dp 行高。 */
@Composable
fun AppListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    rowHeight: Dp = 84.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = AppDimens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listSpacing),
    ) {
        repeat(rows) {
            AppShimmer(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                shape = RoundedCornerShape(AppDimens.cornerMedium),
            )
        }
    }
}
