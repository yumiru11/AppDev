package com.yumiru11.githubapp.di

import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话令牌提供方：从 [TokenStorage] 读取当前凭据注入 REST 请求。
 *
 * P0-7 修复（2026-08-14 真机走查）：原实现把 [TokenProvider] 绑死 [GuestTokenProvider]
 * （永远 null），导致 PAT/OAuth 令牌从不注入请求 → 一律 401。本类在 app 装配层
 * 汇合 core:github-auth（存储）与 core:github-rest（网络），保持两 core 无依赖。
 *
 * 读取顺序与 [com.yumiru11.githubapp.core.githubauth.auth.AuthState] 推导一致：
 * PAT（isRestOnly）→ pat；OAuth → accessToken；无会话 → null（游客匿名）。
 */
@Singleton
class SessionTokenProvider
    @Inject
    constructor(
        private val storage: TokenStorage,
    ) : TokenProvider {
        override fun token(): String? {
            val session = storage.loadSession() ?: return null
            return session.pat ?: session.accessToken
        }
    }
