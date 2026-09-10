package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/**
 * 主题切换的颜色过渡（#167 / UI15，ui-design §3.6 用户拍板 F3-1「主题切换全屏 Crossfade」）。
 *
 * ## 为什么是"动颜色"而不是"动内容"
 *
 * 最直觉的实现是 `Crossfade(targetState = themeMode) { AppTheme(it) { content() } }`，
 * 但那会把整棵内容树按目标态**重新组合一遍**：NavHost 会被重建、滚动位置与输入框状态
 * 都要靠 rememberSaveable 兜，得不偿失。
 *
 * 这里改为**逐色角色插值**：内容树原地保留，只让 `MaterialTheme.colorScheme` 的每个角色
 * 在旧值→新值之间过渡。视觉上就是整屏 Crossfade（所有表面/文字/强调色同时渐变），
 * 却没有一次内容重建 —— 主题切换不再"闪一下"。
 *
 * 时长与曲线走 [AppMotion]（`DURATION_PAGE_ENTER` + Emphasized），并受全局动效缩放约束：
 * 系统「减弱动画」下会瞬时完成，即退化为原来的硬切。
 */
@Composable
fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_PAGE_ENTER)
    val spec = tween<Color>(durationMillis = duration, easing = AppMotion.Emphasized)

    @Composable
    fun anim(value: Color): Color = animateColorAsState(targetValue = value, animationSpec = spec, label = "theme-color").value

    return target.copy(
        primary = anim(target.primary),
        onPrimary = anim(target.onPrimary),
        primaryContainer = anim(target.primaryContainer),
        onPrimaryContainer = anim(target.onPrimaryContainer),
        inversePrimary = anim(target.inversePrimary),
        secondary = anim(target.secondary),
        onSecondary = anim(target.onSecondary),
        secondaryContainer = anim(target.secondaryContainer),
        onSecondaryContainer = anim(target.onSecondaryContainer),
        tertiary = anim(target.tertiary),
        onTertiary = anim(target.onTertiary),
        tertiaryContainer = anim(target.tertiaryContainer),
        onTertiaryContainer = anim(target.onTertiaryContainer),
        background = anim(target.background),
        onBackground = anim(target.onBackground),
        surface = anim(target.surface),
        onSurface = anim(target.onSurface),
        surfaceVariant = anim(target.surfaceVariant),
        onSurfaceVariant = anim(target.onSurfaceVariant),
        surfaceTint = anim(target.surfaceTint),
        inverseSurface = anim(target.inverseSurface),
        inverseOnSurface = anim(target.inverseOnSurface),
        error = anim(target.error),
        onError = anim(target.onError),
        errorContainer = anim(target.errorContainer),
        onErrorContainer = anim(target.onErrorContainer),
        outline = anim(target.outline),
        outlineVariant = anim(target.outlineVariant),
        scrim = anim(target.scrim),
        surfaceBright = anim(target.surfaceBright),
        surfaceDim = anim(target.surfaceDim),
        surfaceContainer = anim(target.surfaceContainer),
        surfaceContainerHigh = anim(target.surfaceContainerHigh),
        surfaceContainerHighest = anim(target.surfaceContainerHighest),
        surfaceContainerLow = anim(target.surfaceContainerLow),
        surfaceContainerLowest = anim(target.surfaceContainerLowest),
    )
}
