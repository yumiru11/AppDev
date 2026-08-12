package com.yumiru11.githubapp.core.githubauth.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 回调 URI → authorization code 提取测试（纯 JVM，不依赖 android.net.Uri）。
 *
 * GitHub OAuth 回调格式：`com.yumiru11.githubapp://oauth-callback?code=xxx&state=yyy`；
 * 错误回调：`?error=access_denied&error_description=...`（无 code，应返回 null）。
 */
class OAuthCallbackParserTest {
    @Test
    fun extractAuthorizationCode_validCallback_returnsCode() {
        assertEquals("abc123", extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback?code=abc123&state=xyz"))
    }

    @Test
    fun extractAuthorizationCode_codeNotFirstParam_returnsCode() {
        assertEquals("abc123", extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback?state=xyz&code=abc123"))
    }

    @Test
    fun extractAuthorizationCode_missingCode_returnsNull() {
        assertNull(extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback?state=xyz"))
    }

    @Test
    fun extractAuthorizationCode_errorCallback_returnsNull() {
        assertNull(extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback?error=access_denied&error_description=user+cancelled"))
    }

    @Test
    fun extractAuthorizationCode_blankCode_returnsNull() {
        assertNull(extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback?code=&state=xyz"))
    }

    @Test
    fun extractAuthorizationCode_noQuery_returnsNull() {
        assertNull(extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback"))
    }

    @Test
    fun extractAuthorizationCode_fragmentAfterQuery_ignoresFragment() {
        assertEquals("abc123", extractAuthorizationCode("com.yumiru11.githubapp://oauth-callback?code=abc123#section"))
    }
}
