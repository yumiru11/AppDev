package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.ui.graphics.Color

/**
 * 全屏浮层专用色（ui-design §6.1 / §3.11 用户逐项拍板）。
 *
 * 这里的纯黑**不是**硬编码颜色违规：§6.1 第 5 项明确「图片全屏查看器改纯黑——
 * 看图沉浸优先」，§3.11 9g 同时要求「向上 fade in 全屏查看（背景纯黑）」。
 * 主题色板里没有"纯黑"这个角色（OLED 的 surfaceContainerLowest 也随主题变化），
 * 故收敛成显式令牌，避免各消费点各写一次 Color.Black。
 */
object AppOverlay {
    /** 图片全屏查看器背景（纯黑，沉浸优先；§6.1 #5 拍板） */
    val imageViewerScrim: Color = Color.Black
}
