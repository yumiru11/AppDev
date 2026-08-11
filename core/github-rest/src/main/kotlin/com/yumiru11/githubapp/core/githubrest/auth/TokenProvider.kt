package com.yumiru11.githubapp.core.githubrest.auth

/**
 * 访问令牌提供者（认证抽象 seam）。
 *
 * OAuth PKCE（AppAuth）在后续认证票接入后提供真实实现；
 * 令牌明文不落日志、不进 WebView（plan.md §4.2）。
 */
fun interface TokenProvider {
    /** 当前有效的访问令牌；未登录（游客模式）返回 null */
    fun token(): String?
}

/** 游客模式：无令牌，仅可访问公共内容（plan.md §4.2） */
class GuestTokenProvider : TokenProvider {
    override fun token(): String? = null
}
