package com.yumiru11.githubapp.di

import com.yumiru11.githubapp.BuildConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OAuthConfig 装配测试（纯 JVM）。
 *
 * 锚定构建期注入链路：app/build.gradle.kts 解析的 oauthClientId 写入
 * [BuildConfig.OAUTH_CLIENT_ID]，[OAuthConfigModule] 必须原样交给 [OAuthConfig]——
 * 防止有人回退成硬编码 `OAuthConfig()` 默认占位符（真机 PKCE 静默失效）。
 */
class OAuthConfigModuleTest {
    @Test
    fun provideOAuthConfig_usesBuildConfigClientId() {
        val config = OAuthConfigModule.provideOAuthConfig()

        assertEquals(
            "client id 必须取自 BuildConfig.OAUTH_CLIENT_ID（构建期注入）",
            BuildConfig.OAUTH_CLIENT_ID,
            config.clientId,
        )
    }

    @Test
    fun provideOAuthConfig_clientIdState_matchesPlaceholderFallback() {
        val config = OAuthConfigModule.provideOAuthConfig()
        val injected = BuildConfig.OAUTH_CLIENT_ID != OAuthConfig.PLACEHOLDER_CLIENT_ID

        // 未注入（CI/本地默认）：占位符回退且 isConfigured=false，不崩溃；
        // 已注入（-P/local.properties/env）：isConfigured=true。
        assertEquals("isConfigured 应与 BuildConfig 注入状态一致", injected, config.isConfigured)
    }
}
