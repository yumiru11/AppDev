package com.yumiru11.githubapp.core.github_auth.auth

import com.yumiru11.githubapp.core.github_auth.token.SessionData

/**
 * 认证状态（UI 层消费的单一事实来源，plan.md §10.1 状态管理）。
 *
 * - [SignedIn]：OAuth 会话（access/refresh token 已持久化），携带 [SessionData] 快照
 * - [PAT]：开发者 PAT 模式（isRestOnly，ADR-0003，仅 REST 通道）
 * - [Anonymous]：未登录/已登出
 *
 * 由 [OAuthSessionManager.authState] 对外暴露，变化即 UI 重新编排（登录页 ↔ 主界面）。
 */
sealed interface AuthState {

    /** OAuth 会话已建立。 */
    data class SignedIn(val session: SessionData) : AuthState

    /** 开发者 PAT 模式（fine-grained PAT 不支持 GraphQL → REST-only，ADR-0003）。 */
    data object PAT : AuthState

    /** 未登录。 */
    data object Anonymous : AuthState
}
