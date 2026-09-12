package com.yumiru11.githubapp.core.githubrest.http

import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import java.security.MessageDigest

/**
 * ETag 缓存的账号作用域提供者（plan.md §4.6；残余审计 DATA-1 / 安全约束）。
 *
 * 缓存体可能含私有仓库数据，必须按账号隔离：同一 [EtagStore] 上，只有同一作用域写入的条目
 * 才会被读取。[scope][currentScope] 在每条请求上现算，登出/切换账号后作用域随之变化。
 */
fun interface EtagScopeProvider {
    /** 当前请求应归属的缓存作用域。 */
    fun currentScope(): String

    companion object {
        /** 未认证（游客）作用域：只会命中公开内容。 */
        const val ANONYMOUS_SCOPE: String = "anonymous"
    }
}

/** 游客作用域：无凭据时使用（公开内容缓存，跨会话安全）。 */
object GuestEtagScopeProvider : EtagScopeProvider {
    override fun currentScope(): String = EtagScopeProvider.ANONYMOUS_SCOPE
}

/**
 * 按当前凭据派生作用域：`scope = SHA-256(bearer token)`，无凭据时为 [EtagScopeProvider.ANONYMOUS_SCOPE]。
 *
 * 用凭据摘要而不是登录名，是因为登录名不落盘（[com.yumiru11.githubapp.core.githubauth.token.SessionData]
 * 只有 token 字段）；摘要是一把**不可逆**的分区键——**绝不持久化原文 token**，泄漏缓存库也无法
 * 反推出可用凭据。不同账号（乃至同账号的 PAT / OAuth 两种凭据）天然落到不同分区，即使登出清空
 * 逻辑回归，也读不到别人的缓存体（纵深防御）。
 */
class TokenEtagScopeProvider(
    private val tokenProvider: TokenProvider,
) : EtagScopeProvider {
    override fun currentScope(): String {
        val token = tokenProvider.token()
        return if (token.isNullOrEmpty()) {
            EtagScopeProvider.ANONYMOUS_SCOPE
        } else {
            sha256Hex(token)
        }
    }
}

/** SHA-256 → 小写十六进制（作用域键；不可逆，非凭据本身）。 */
internal fun sha256Hex(value: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
