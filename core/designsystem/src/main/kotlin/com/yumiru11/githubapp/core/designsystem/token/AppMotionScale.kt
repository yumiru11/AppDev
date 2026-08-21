package com.yumiru11.githubapp.core.designsystem.token

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * 动效缩放基建（#84，ui-design §4.4）。
 *
 * 生效缩放 = min(DataStore motionScale, 系统动画时长缩放)，二者取最小：
 * - 设置页「动画强度」滑杆（0.5–1.5，默认 1f）经 AppTheme 注入 [LocalMotionScale]
 * - 系统「动画时长缩放」（开发者选项）或无障碍「移除动画」(= 0) 经
 *   [rememberSystemMotionScale] 读取 —— 尊重系统「减弱动画」是硬性约束
 *
 * 消费入口统一走 [AppMotion.scaledDuration]，禁止各处自算时长。
 */
val LocalMotionScale = staticCompositionLocalOf { 1f }

/** mirror of the DataStore motionScale slider bounds（UserPreferencesRepository 契约）. */
const val MAX_MOTION_SCALE: Float = 1.5f

/**
 * 计算生效动效缩放的纯函数（可单测）：
 * 取用户滑杆与系统缩放的较小值；负数输入按 0 处理（即时完成），上限 [MAX_MOTION_SCALE]。
 */
fun resolveEffectiveMotionScale(
    userScale: Float,
    systemScale: Float,
): Float = minOf(userScale, systemScale).coerceIn(0f, MAX_MOTION_SCALE)

/**
 * 读系统「动画时长缩放」（Settings.Global.ANIMATOR_DURATION_SCALE，默认 1f）。
 *
 * 会话内只读一次（[remember] 无 key）：运行中改开发者选项需重启进程才生效，
 * 与原生应用行为一致。无障碍「移除动画」会把它置 0 → 全部动效即时完成。
 */
@Composable
fun rememberSystemMotionScale(): Float {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
}
