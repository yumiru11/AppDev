package com.yumiru11.githubapp.feature.auth

/**
 * 登录页导航事件（不改变登录态、仅驱动导航的 UI 意图）。
 *
 * 与 [AuthViewModel.authState] 区分：authState 变化由登录态驱动导航（LaunchedEffect），
 * 本通道处理「登录态不变但需导航」的场景（游客浏览 → 主页）。
 */
sealed interface AuthNavigation {
    /** 游客浏览 → 主页。 */
    data object Home : AuthNavigation
}
