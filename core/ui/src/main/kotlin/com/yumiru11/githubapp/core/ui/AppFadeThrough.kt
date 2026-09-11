package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/** M3 fade-through 进场初始缩放：新内容自 92% 放大到位（官方 fade-through 规格）。 */
const val FADE_THROUGH_INITIAL_SCALE: Float = 0.92f

/**
 * M3 **fade-through** 过渡规格（#167 / UI16 + UI17，ui-design §4.3「渐入渐出」）。
 *
 * 语义 = "同一块区域换内容"：旧内容先在 [exitMillis] 内淡出（并缩到 92%，
 * Emphasized accelerate），新内容延迟同样的时长后淡入 + 自 92% 放大
 * （Emphasized decelerate）。适用点见 §4.3：结果区刷新、分区切换。
 *
 * M3 官方把 fade-through 定为 300ms（出场 90ms + 进场 210ms，见
 * [AppMotion.DURATION_FADE_THROUGH_OUT] / [AppMotion.DURATION_FADE_THROUGH_IN]）。
 *
 * ## 为什么是纯函数（非 @Composable）
 *
 * `AnimatedContent` 的 `transitionSpec` 是**普通函数类型**，内部无法调用 @Composable 的
 * [AppMotion.scaledDuration]（同 AppNavHost 转场先例）。因此时长由调用方在组合内折算后传入：
 * 传 0（系统「移除动画」或滑杆到底）即直接切换，**不进入动画路径**（§4.4 约束）。
 */
fun appFadeThroughTransform(
    enterMillis: Int,
    exitMillis: Int,
): ContentTransform {
    val outMillis = exitMillis.coerceAtLeast(0)
    val inMillis = enterMillis.coerceAtLeast(0)
    if (outMillis == 0 && inMillis == 0) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val enter =
        fadeIn(
            animationSpec = tween(inMillis, delayMillis = outMillis, easing = AppMotion.EmphasizedDecelerate),
        ) +
            scaleIn(
                initialScale = FADE_THROUGH_INITIAL_SCALE,
                animationSpec = tween(inMillis, delayMillis = outMillis, easing = AppMotion.EmphasizedDecelerate),
            )
    val exit =
        fadeOut(animationSpec = tween(outMillis, easing = AppMotion.EmphasizedAccelerate)) +
            scaleOut(
                targetScale = FADE_THROUGH_INITIAL_SCALE,
                animationSpec = tween(outMillis, easing = AppMotion.EmphasizedAccelerate),
            )
    return enter togetherWith exit
}
