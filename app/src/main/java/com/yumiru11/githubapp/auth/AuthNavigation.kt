package com.yumiru11.githubapp.auth

import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.navigation.AppRoute

/**
 * 登录态 → 导航目标映射（T4 Wave2 登录态驱动首屏）。
 *
 * 游客直进首页为真机走查决策（P0-2）；登录页仅显式入口可达。
 * #90 起返回类型安全路由对象 [AppRoute.Home]。
 */
fun authStateToDestination(authState: AuthState): AppRoute = AppRoute.Home

/**
 * 登录态导航守卫：仅当需要切换登录门时才导航，避免导航循环与深链被打断。
 *
 * - 目标为登录页（Anonymous）：当前不在登录页才导航（游客/深链场景回到登录门）
 * - 目标为主页（SignedIn/PAT）：仅当还停在登录页时导航（登录成功跳主页；
 *   已登录 + 深链直达详情页时不打断深链导航）
 *
 * #90 签名现代化：接收「当前是否在登录页」布尔（由调用方经
 * `navController.currentDestination?.hasRoute<AppRoute.Login>()` 判定），
 * 替代旧字符串 currentRoute 比较，保持纯函数可单测。
 */
fun shouldNavigateForAuthState(
    isCurrentlyOnLogin: Boolean,
    target: AppRoute,
): Boolean =
    when (target) {
        is AppRoute.Login -> !isCurrentlyOnLogin
        else -> isCurrentlyOnLogin
    }
