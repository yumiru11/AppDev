package com.yumiru11.githubapp.core.githubauth.auth

/**
 * GitHub OAuth 授权配置（plan.md §4.1，ADR-0001）。
 *
 * @property clientId OAuth App 的 client ID。默认 [PLACEHOLDER_CLIENT_ID] 为**占位符**：
 *   真实 client ID 需开发者到 GitHub 创建 OAuth App 后在此替换（公开客户端，无 client_secret）。
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
    companion object {
        /** 占位 client ID——真实值需用户配 OAuth App 后填写（见 KDoc）。 */
        const val PLACEHOLDER_CLIENT_ID = "YOUR_OAUTH_APP_CLIENT_ID"

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
