package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/**
 * 临时页面（BottomSheet）内容进场（#167 / UI17，ui-design §4.1）。
 *
 * §4.1 表定「页面进出（临时，BottomSheet/Drawer）= Emphasized **500ms**」。
 * M3 的 `ModalBottomSheet` 把容器的展示/隐藏 AnimationSpec 锁在 `SheetState` 内部
 * （`showMotionSpec$material3` 为 internal，跨模块不可达），因此容器滑动只能沿用 M3
 * 默认（它本身尊重系统动画缩放）；本修饰符负责把**内容层**的
 * slide+fade 走 [AppMotion.DURATION_TRANSIENT] 令牌（§3.9/§3.10「评论输入框 Slide+fade」），
 * 使设置页「动画强度」滑杆同样作用于 BottomSheet。
 *
 * - 时长经 [AppMotion.scaledDuration] 折算；折算为 0（系统「移除动画」）→ 直接呈现终态
 * - 只动 alpha，不改布局/测量：Sheet 内的 TextField 聚焦、IME 与既有测试不受影响
 */
@Composable
fun Modifier.appTransientEnterAlpha(): Modifier {
    val durationMillis = AppMotion.scaledDuration(AppMotion.DURATION_TRANSIENT)
    val alpha = remember { Animatable(if (durationMillis <= 0) 1f else 0f) }
    LaunchedEffect(durationMillis) {
        if (durationMillis <= 0) {
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = AppMotion.Emphasized),
        )
    }
    return this.graphicsLayer { this.alpha = alpha.value }
}
