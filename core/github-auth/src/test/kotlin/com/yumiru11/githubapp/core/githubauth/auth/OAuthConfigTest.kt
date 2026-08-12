package com.yumiru11.githubapp.core.githubauth.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OAuthConfig 常量正确性测试（纯 JVM）。
 *
 * 锚定 ADR-0001（自定义 scheme 回调）与 plan.md §4.1（GitHub OAuth 端点/scope 最小集）。
 */
class OAuthConfigTest {
    @Test
    fun oauthConfig_defaults_matchAdr0001AndMinimalScopes() {
        val config = OAuthConfig()

        assertEquals("回调 URI 应为自定义 scheme（ADR-0001）", "com.yumiru11.githubapp://oauth-callback", config.redirectUri)
        assertEquals("授权端点应为 GitHub", "https://github.com/login/oauth/authorize", config.authorizeEndpoint)
        assertEquals("token 端点应为 GitHub", "https://github.com/login/oauth/access_token", config.tokenEndpoint)
        assertEquals("scope 应为合理最小集", "repo read:user", config.scopes)
        assertEquals("默认 clientId 应为占位符", OAuthConfig.PLACEHOLDER_CLIENT_ID, config.clientId)
    }

    @Test
    fun oauthConfig_companionConstants_matchDefaults() {
        assertEquals(OAuthConfig.REDIRECT_URI, OAuthConfig().redirectUri)
        assertEquals(OAuthConfig.GITHUB_AUTHORIZE_ENDPOINT, OAuthConfig().authorizeEndpoint)
        assertEquals(OAuthConfig.GITHUB_TOKEN_ENDPOINT, OAuthConfig().tokenEndpoint)
        assertEquals(OAuthConfig.DEFAULT_SCOPES, OAuthConfig().scopes)
    }

    @Test
    fun oauthConfig_customTokenEndpoint_overridesDefault() {
        val config = OAuthConfig(tokenEndpoint = "https://mock.example.com/token")
        assertEquals("https://mock.example.com/token", config.tokenEndpoint)
        // 其余字段保持默认（测试注入时只改需要改的）
        assertEquals(OAuthConfig.REDIRECT_URI, config.redirectUri)
    }
}
