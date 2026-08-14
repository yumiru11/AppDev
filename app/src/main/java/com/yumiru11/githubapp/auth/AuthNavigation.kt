package com.yumiru11.githubapp.auth

import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.navigation.AppRoute

/**
 * 登录态 → 导航目标映射（T4 Wave2 登录态驱动首屏）。
 *
 * - [AuthState.Anonymous] → 登录页
 * - [AuthState.SignedIn] / [AuthState.PAT] → 主页
 */
fun authStateToDestination(authState: AuthState): String =
    when (authState) {
        is AuthState.Anonymous -> AppRoute.HOME
        is AuthState.SignedIn -> AppRoute.HOME
        is AuthState.PAT -> AppRoute.HOME
    }

/**
 * 登录态导航守卫：仅当需要切换登录门时才导航，避免导航循环与深链被打断。
 *
 * - 目标为登录页（Anonymous）：当前不在登录页才导航（游客/深链场景回到登录门）
 * - 目标为主页（SignedIn/PAT）：仅当还停在登录页时导航（登录成功跳主页；
 *   已登录 + 深链直达详情页时不打断深链导航）
 */
fun shouldNavigateForAuthState(
    currentRoute: String?,
    target: String,
): Boolean =
    when (target) {
        AppRoute.LOGIN -> currentRoute != AppRoute.LOGIN
        else -> currentRoute == AppRoute.LOGIN
    }
