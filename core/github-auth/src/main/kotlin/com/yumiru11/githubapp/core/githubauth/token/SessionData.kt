package com.yumiru11.githubapp.core.githubauth.token

import kotlinx.serialization.Serializable

/**
 * 会话凭据快照（ADR-0002）。
 *
 * - [accessToken] / [refreshToken]：OAuth PKCE 授权码流程换取（plan.md §4.1），
 *   GraphQL + REST 双通道可用
 * - [pat]：Personal Access Token，仅开发者模式；fine-grained PAT 不支持 GraphQL → REST-only
 * - [isRestOnly]：降级标记，true 表示当前凭据仅 REST 通道可用（无 GraphQL 权限）
 *
 * 凭据字段为 null 即表示该凭据不存在；accessToken 与 pat 互斥（登录流程保证）。
 */
@Serializable
data class SessionData(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val pat: String? = null,
    val isRestOnly: Boolean = false,
)
