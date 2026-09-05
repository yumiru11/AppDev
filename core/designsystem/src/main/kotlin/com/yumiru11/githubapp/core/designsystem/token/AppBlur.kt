package com.yumiru11.githubapp.core.designsystem.token

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 毛玻璃（Glassmorphism）设计令牌。
 *
 * Values sourced from docs/ui-design.md §6: backdrop blur via Haze
 * （RenderEffect，API 31+）on Android 12+，translucent surface fallback on
 * API 26–30 (no bitmap blur, performance first). Glass is restricted to the
 * §6.1 allow-list (top bar / bottom navigation / bottom sheet / full-screen
 * viewer / banner overlay); never inside list items, never stacked beyond
 * 2 layers, no dynamic blur.
 *
 * 「本机到底走模糊还是走降级」不在本令牌内判定——见 [GlassRenderPolicy]
 * （issue #83：判定收敛成纯函数后才能被单测断言）。
 */
object AppBlur {
    /** 标准模糊半径（ui-design.md §6.3 拍板：中 8dp；CONTEXT.md 玻璃清单同值） */
    val blurRadius: Dp = 8.dp

    /**
     * 半透明纯色层的 alpha（§6.1 静止态 / §6.2 API 26–30 降级层）。
     * 模糊层之上叠加该 alpha 的 surface 色以保持可读性。
     */
    const val SCRIM_ALPHA: Float = 0.75f

    /** 支持真实模糊（RenderEffect/BlurEffect）的最低 API — Android 12 */
    const val MIN_BLUR_API: Int = Build.VERSION_CODES.S
}
