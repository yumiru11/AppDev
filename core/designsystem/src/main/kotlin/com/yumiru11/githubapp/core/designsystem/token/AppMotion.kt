package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring

/**
 * M3 motion tokens — durations, easing curves, and spring physics.
 *
 * Values sourced from docs/ui-design.md §4 (M3 Motion), aligned with the
 * official M3 easing-and-duration spec (m3.material.io). All animation
 * durations must come from here; honour the system "remove animations"
 * setting via a global animationScale factor (see §4.3).
 */
object AppMotion {
    // ── Durations (ms) ──

    /** 页面进入（大转场）— Emphasized decelerate */
    const val DURATION_PAGE_ENTER: Int = 400

    /** 页面退出（永久）— Emphasized accelerate */
    const val DURATION_PAGE_EXIT: Int = 200

    /** 页面进出（临时，BottomSheet/Drawer）— Emphasized */
    const val DURATION_TRANSIENT: Int = 500

    /** 元素进出屏幕（列表项）— Emphasized decelerate */
    const val DURATION_LIST_ITEM: Int = 300

    /** 小型状态变化（图标填充/勾选）— Standard */
    const val DURATION_SMALL_STATE_CHANGE: Int = 200

    /** 按压反馈 — Standard accelerate */
    const val DURATION_PRESS_FEEDBACK: Int = 150

    // ── Easing ──

    /** M3 Emphasized（大转场：短暂快速加速后长程缓慢减速） */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** M3 Emphasized decelerate（页面进入） */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** M3 Emphasized accelerate（页面退出） */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // ── Spring（回弹，§4.2） ──

    /** 阻尼比 HighBouncy — Star/收藏/点赞图标、头像点击、「全部已读」回弹 */
    val DampingRatioHighBouncy: Float = Spring.DampingRatioHighBouncy

    /** 刚度 Medium — 与 [DampingRatioHighBouncy] 搭配使用 */
    val StiffnessMedium: Float = Spring.StiffnessMedium

    // ── List stagger（§4.2 叠加） ──

    /** LazyColumn 首屏进入动画的 stagger 间隔 */
    const val LIST_STAGGER_INTERVAL_MILLIS: Int = 24
}
