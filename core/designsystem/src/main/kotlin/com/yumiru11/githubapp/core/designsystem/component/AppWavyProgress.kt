package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.LocalMotionScale
import kotlin.math.PI
import kotlin.math.sin

/**
 * 波浪式不确定进度：三条相位错开的正弦曲线横向行进（Expressive 语言的形变加载指示）。
 *
 * 与 [AppLoadingState] 的分工：整页首载用形变 LoadingIndicator；本组件用于
 * 栏位内 / 列表顶部这类**窄条**场景（下拉刷新指示、行内加载）。
 *
 * 动效契约：动效强度为 0（设置页滑杆归零或系统关闭动画）时相位冻结为静态波形，
 * 不再产生逐帧重绘——与 [com.yumiru11.githubapp.core.designsystem.token.AppMotionScheme]
 * 的约定一致。
 */
@Composable
fun AppWavyProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
) {
    val motionScale = LocalMotionScale.current
    val transition = rememberInfiniteTransition(label = "wavy")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase",
    )
    val phase = if (motionScale > 0f) animatedPhase else 0f
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val amplitude = height / 6f
        val wavelength = (width / 1.6f).coerceAtLeast(1f)
        val step = 4f
        repeat(WAVE_COUNT) { index ->
            val offsetY = height / 2f + (index - 1) * (height / 3.2f)
            val alpha = (1f - index * 0.28f).coerceAtLeast(0.2f)
            val path = Path()
            var x = 0f
            while (x <= width) {
                val y = offsetY + amplitude * sin((x / wavelength + phase + index * 0.12f) * 2f * PI).toFloat()
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            drawPath(path = path, color = color.copy(alpha = alpha), style = Stroke(width = strokeWidth.toPx()))
        }
    }
}

private const val WAVE_COUNT = 3
