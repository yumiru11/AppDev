package com.yumiru11.githubapp.core.designsystem.component

import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 毛玻璃 backdrop blur 的共享 [HazeState]（issue #83，ui-audit 缺陷 #1）。
 *
 * Haze 模型：**内容侧**挂 `Modifier.hazeSource(state)`（被模糊的滚动内容），
 * **玻璃栏侧**挂 `Modifier.hazeEffect(state)`（[GlassSurface]），两侧共享同一 state。
 * 因此 state 必须由「同时拥有栏与内容」的容器组件提供：
 *
 * - [com.yumiru11.githubapp.core.ui.MainTabPager] 提供并作用于底栏 + 分区内容；
 * - HomeScreen 自建一份覆盖其子树（顶栏 + feed 内容），屏蔽外层底栏 state，
 *   避免顶栏 effect 落进底栏 source 子树的嵌套歧义。
 *
 * 默认 `null`：玻璃层读不到 state 时退化为纯半透明 scrim（与 API 26–30 / 关闭
 * 开关的降级路径一致），保证 GlassSurface 在无提供者的任意场景安全可用。
 *
 * 用 staticCompositionLocalOf：state 在组合期内不变（rememberHazeState 产物），
 * 无需动态重组传播。
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
