package com.yumiru11.githubapp.core.designsystem.token

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 毛玻璃（Glassmorphism）设计令牌。
 *
 * Values sourced from docs/ui-design.md §6: real blur via `RenderEffect`/
 * `BlurEffect` on Android 12+ (API 31+), translucent surface fallback on
 * API 26–30 (no bitmap blur, performance first). Glass is restricted to the
 * §6.1 allow-list (top bar / bottom navigation / bottom sheet / full-screen
 * viewer / banner overlay); never inside list items, never stacked beyond
 * 2 layers, no dynamic blur.
 */
object AppBlur {
    /** 标准模糊半径（§6.2：12dp） */
    val blurRadius: Dp = 12.dp

    /**
     * 半透明纯色层的 alpha（§6.1 静止态 / §6.2 API 26–30 降级层）。
     * 模糊层之上叠加该 alpha 的 surface 色以保持可读性。
     */
    const val scrimAlpha: Float = 0.75f

    /** 支持真实模糊（RenderEffect/BlurEffect）的最低 API — Android 12 */
    const val minBlurApi: Int = Build.VERSION_CODES.S

    /** 当前设备是否支持真实模糊（API 31+）；否则降级为半透明纯色层 */
    fun isBlurSupported(): Boolean = Build.VERSION.SDK_INT >= minBlurApi
}
