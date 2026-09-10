package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局 stagger 开关（#167 / UI06，ui-design §4.2 H2-2「列表 stagger：可选开关（设置里）」）。
 *
 * 用户拍板把「首屏列表逐项入场」做成**可关**的：stagger 是"锦上添花"的动效，
 * 但在低端机或长列表上会让人觉得"内容在排队出现"。关掉即退化为一次性直出（无位移动画）。
 *
 * 与 [LocalMotionScale] 的分工：
 * - [LocalMotionScale] 控制**所有**动效的时长缩放（含系统「减弱动画」）
 * - [LocalStaggerEnabled] 只控制"逐项错峰"这一个特性，不影响其它动效
 *
 * 两者都关/为 0 时行为一致（列表直出），但语义不同，不要合并成一个。
 */
val LocalStaggerEnabled = staticCompositionLocalOf { true }
