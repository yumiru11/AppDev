package com.yumiru11.githubapp.core.github_auth.session

import com.yumiru11.githubapp.core.github_auth.token.SessionData
import com.yumiru11.githubapp.core.github_auth.token.TokenStorage

/**
 * PAT 降级判定（ADR-0003）：REST-only = 当前凭据仅 REST 通道可用（无 GraphQL）。
 */
fun isRestOnly(session: SessionData): Boolean = session.isRestOnly

/**
 * PAT 模式登录：纯存储，不换 token（PAT 即最终凭据）。
 *
 * fine-grained PAT 不支持 GraphQL（ADR-0003），登录即降级：
 * 写入 pat 并标记 isRestOnly=true，整体覆盖旧会话（含 OAuth 凭据）。
 */
suspend fun loginWithPat(
    tokenStorage: TokenStorage,
    pat: String,
): SessionData {
    require(pat.isNotBlank())
    val session = SessionData(pat = pat, isRestOnly = true)
    tokenStorage.saveSession(session)
    return session
}
