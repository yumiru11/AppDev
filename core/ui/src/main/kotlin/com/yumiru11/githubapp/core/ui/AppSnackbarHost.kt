package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import kotlinx.coroutines.delay

/**
 * 应用级 Snackbar 宿主（#167 / UI17，ui-design §4.3「渐入渐出」）。
 *
 * 与 M3 `SnackbarHost` 行为等价（自动消失 + 退出期间保留上一条 + a11y 时长），
 * 唯一差别是**进出动效时长走 [AppMotion] 令牌**：M3 把 Snackbar 的时长硬编码在
 * `FadeInFadeOutWithScale`（150ms / 75ms，且不含设置页「动画强度」滑杆），
 * 这里改为 §4.1 表的「元素进出屏幕 300ms Emphasized decelerate」进、
 * 「页面退出 200ms Emphasized accelerate」出，二者都经 [AppMotion.scaledDuration]
 * 折算（设置滑杆 × 系统动画缩放取 min；折算为 0 = 直接呈现，不进入动画路径）。
 *
 * ⚠️ 自动消失必须自己实现：M3 的 `SnackbarHostState.showSnackbar` 会**挂起到本条被 dismiss
 * 为止**，而按时长 dismiss 的 `LaunchedEffect` 住在 `SnackbarHost` 内部——绕开它而不补这段，
 * Snackbar 就会永远留在屏幕上（调用方的 showSnackbar 也永不返回）。
 *
 * @param hostState 调用方持有的宿主状态（与 M3 同源，`remember { SnackbarHostState() }`）
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val currentData = hostState.currentSnackbarData
    val accessibilityManager = LocalAccessibilityManager.current

    // dismiss 之后 currentSnackbarData 立刻变 null，但退出动画还要把这条画完
    // （M3 SnackbarHost 内部同样保留上一条），故留一份"正在退场"的数据。
    var exitingData by remember { mutableStateOf<SnackbarData?>(null) }
    LaunchedEffect(currentData) {
        val data = currentData ?: return@LaunchedEffect
        exitingData = data
        delay(
            data.visuals.duration.durationMillis(
                hasAction = data.visuals.actionLabel != null,
                accessibilityManager = accessibilityManager,
            ),
        )
        data.dismiss()
    }
    val shown = currentData ?: exitingData

    val enterMillis = AppMotion.scaledDuration(AppMotion.DURATION_LIST_ITEM)
    val exitMillis = AppMotion.scaledDuration(AppMotion.DURATION_PAGE_EXIT)
    AnimatedVisibility(
        visible = currentData != null,
        modifier = modifier,
        enter =
            fadeIn(animationSpec = tween(enterMillis, easing = AppMotion.EmphasizedDecelerate)) +
                slideInVertically(
                    animationSpec = tween(enterMillis, easing = AppMotion.EmphasizedDecelerate),
                    initialOffsetY = { height -> height },
                ),
        exit =
            fadeOut(animationSpec = tween(exitMillis, easing = AppMotion.EmphasizedAccelerate)) +
                slideOutVertically(
                    animationSpec = tween(exitMillis, easing = AppMotion.EmphasizedAccelerate),
                    targetOffsetY = { height -> height },
                ),
    ) {
        shown?.let { Snackbar(snackbarData = it) }
    }
}

/**
 * Snackbar 展示时长（毫秒）——M3 `SnackbarHostKt.toMillis` 的同款语义（internal，跨模块不可达，
 * 故在此按同值重写）：Short 4s / Long 10s / Indefinite 永不自动消失，
 * 并按无障碍推荐超时放大（阅读障碍用户需要更长停留）。
 */
private fun SnackbarDuration.durationMillis(
    hasAction: Boolean,
    accessibilityManager: AccessibilityManager?,
): Long {
    val base =
        when (this) {
            SnackbarDuration.Short -> SHORT_MILLIS
            SnackbarDuration.Long -> LONG_MILLIS
            SnackbarDuration.Indefinite -> Long.MAX_VALUE
        }
    // 无障碍管理器缺失（无读屏/旧版本）时按基础时长走
    return accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = base,
        containsIcons = true,
        containsText = true,
        containsControls = hasAction,
    ) ?: base
}

private const val SHORT_MILLIS = 4_000L
private const val LONG_MILLIS = 10_000L
