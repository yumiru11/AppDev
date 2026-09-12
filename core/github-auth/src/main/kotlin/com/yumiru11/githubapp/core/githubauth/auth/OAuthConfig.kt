package com.yumiru11.githubapp.core.githubauth.auth

/**
 * GitHub OAuth 授权配置（plan.md §4.1，ADR-0001）。
 *
 * @property clientId OAuth App 的 client ID（公开客户端，无 client_secret）。生产值由 :app 在构建期
 *   注入（app/build.gradle.kts → BuildConfig.OAUTH_CLIENT_ID → app 装配层构造本类）；默认
 *   [PLACEHOLDER_CLIENT_ID] 仅供测试/CI，用 [isConfigured] 判断真实值是否已配置。
 * @property redirectUri 回调 URI：自定义 scheme（ADR-0001），与 https://github.com 深链隔离。
 * @property authorizeEndpoint 授权端点（浏览器拉起）。
 * @property tokenEndpoint token 端点（授权码换 token / 刷新）。
 * @property scopes 申请 scope 合理最小集：`repo`（仓库/Issue/PR 读写）+ `read:user`（用户资料）。
 */
data class OAuthConfig(
    val clientId: String = PLACEHOLDER_CLIENT_ID,
    val redirectUri: String = REDIRECT_URI,
    val authorizeEndpoint: String = GITHUB_AUTHORIZE_ENDPOINT,
    val tokenEndpoint: String = GITHUB_TOKEN_ENDPOINT,
    val scopes: String = DEFAULT_SCOPES,
) {
    /** client id 是否已配置真实值（非空白、非占位符）——真机 PKCE 授权的前置条件。 */
    val isConfigured: Boolean
        get() = isClientIdConfigured(clientId)

    companion object {
        /** 占位 client ID——真实值需用户配 OAuth App 后填写（见 KDoc）。 */
        const val PLACEHOLDER_CLIENT_ID = "YOUR_OAUTH_APP_CLIENT_ID"

        /** 纯判定：client id 可用（去空白后非空且非占位符）→ true。 */
        fun isClientIdConfigured(clientId: String): Boolean {
            val trimmed = clientId.trim()
            return trimmed.isNotEmpty() && trimmed != PLACEHOLDER_CLIENT_ID
        }

        /** 回调 URI（ADR-0001：自定义 scheme，与 https://github.com 深链隔离）。 */
        const val REDIRECT_URI = "com.yumiru11.githubapp://oauth-callback"

        /** GitHub 授权端点。 */
        const val GITHUB_AUTHORIZE_ENDPOINT = "https://github.com/login/oauth/authorize"

        /** GitHub token 端点（授权码换 token / 刷新）。 */
        const val GITHUB_TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token"

        /** 申请 scope 合理最小集。 */
        const val DEFAULT_SCOPES = "repo read:user"
    }
}
