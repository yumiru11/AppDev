package com.yumiru11.githubapp.di

import com.yumiru11.githubapp.BuildConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * OAuthConfig 绑定（:app 装配层，T4 认证接线）。
 *
 * core:github-auth 不感知 BuildConfig（Konsist 禁止 core:github-* 依赖 app），
 * 故 client id 的**构建期注入点在 app**：app/build.gradle.kts 解析
 * `-PoauthClientId` / `local.properties:oauthClientId` / 环境变量 `OAUTH_CLIENT_ID`，
 * 写入 [BuildConfig.OAUTH_CLIENT_ID]，此处构造 [OAuthConfig] 时传入（全图唯一绑定）。
 *
 * 未配置时 BuildConfig 值即 [OAuthConfig.PLACEHOLDER_CLIENT_ID]，构建/测试照常，
 * 仅真机 PKCE 授权会失败——用 [OAuthConfig.isConfigured] 判定。
 */
@Module
@InstallIn(SingletonComponent::class)
object OAuthConfigModule {
    @Provides
    @Singleton
    fun provideOAuthConfig(): OAuthConfig = OAuthConfig(clientId = BuildConfig.OAUTH_CLIENT_ID)
}
