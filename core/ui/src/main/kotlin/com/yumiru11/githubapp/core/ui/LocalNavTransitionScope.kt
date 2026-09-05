@file:OptIn(ExperimentalSharedTransitionApi::class)
// #90 共享元素试点：Compose 1.11 的 sharedElement/rememberSharedContentState 为
// ExperimentalSharedTransitionApi（BOM 2026.06.01 解析到 1.11.4）

package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * #90 共享元素试点：Navigation 过渡作用域注入器。
 *
 * - [LocalSharedTransitionScope]：由 [AppNavHost] 的 SharedTransitionLayout 在
 *   NavHost 外围 provide；null（Preview/独立截图测试）表示不在共享过渡上下文中
 * - [LocalNavTransitionScope]：由 [AppNavHost] 在每个 destination 内 provide 该
 *   destination 的 [AnimatedVisibilityScope]；null 同理
 *
 * 两个 local 都判空后 [sharedTransitionElement] 退化为普通 Modifier，
 * 因此 feature 屏无需感知导航上下文，单屏独立渲染/测试不受影响。
 */
val LocalSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

/** 当前 NavHost destination 的动画作用域（见 [LocalSharedTransitionScope] KDoc）。 */
val LocalNavTransitionScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 共享元素过渡修饰符（列表头像 → 详情页头像试点，#90）。
 *
 * 两屏以相同 [key]（如 `repo-avatar-${ownerLogin}`）应用本修饰符，push/pop 时
 * 小头像平滑放大为详情头像。不在导航过渡上下文（scope 为 null）时原样返回。
 *
 * 注意：key 全局唯一即可，两端各自 rememberSharedContentState 由框架按 key 配对。
 */
@Composable
fun Modifier.sharedTransitionElement(key: String): Modifier {
    val sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope: AnimatedVisibilityScope = LocalNavTransitionScope.current ?: return this
    // Compose 1.11：sharedElement 是 SharedTransitionScope 接口内的 Modifier 扩展成员
    // （receiver=Modifier，scope 为上下文），rememberSharedContentState 为 scope 成员；
    // 其余参数走默认（boundsTransform/placeholder/zIndex 等）
    return with(sharedTransitionScope) {
        this@sharedTransitionElement.sharedElement(
            sharedContentState = sharedTransitionScope.rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
