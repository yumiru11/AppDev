package com.yumiru11.githubapp.core.githubauth.auth

import com.yumiru11.githubapp.core.githubauth.token.EncryptedTokenStorage
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 认证层 Hilt 装配（SingletonComponent）。
 *
 * - [TokenStorage] → [EncryptedTokenStorage]（生产：EncryptedSharedPreferences 加密落盘，ADR-0002）
 * - [TokenEndpointClient] → [OkHttpTokenEndpointClient]（授权码换 token HTTP 层）
 *
 * [OAuthConfig] 不在本模块绑定：由 :app 装配层提供（BuildConfig.OAUTH_CLIENT_ID 构建期注入，
 * 见 app 的 OAuthConfigModule）——core 不感知 BuildConfig，测试/CI 以默认占位符构造。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindTokenStorage(impl: EncryptedTokenStorage): TokenStorage

    @Binds
    @Singleton
    abstract fun bindTokenEndpointClient(impl: OkHttpTokenEndpointClient): TokenEndpointClient
}

/**
 * 认证配置装配：裸 OkHttpClient（[OAuthConfig] 绑定已上移 :app 装配层，见 [AuthModule] KDoc）。
 *
 * 认证流程的 OkHttpClient 用独立限定符（[AuthHttpClient]），与 core:github-rest 的
 * [@GitHubHttpClient] 隔离：token 端点请求必须走裸 client（不注入凭据、不参与 401 刷新）。
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthConfigModule {
    @Provides
    @Singleton
    @AuthHttpClient
    fun provideAuthOkHttpClient(): OkHttpClient = OkHttpClient()
}

/** 限定认证流程专用 OkHttpClient（与 GitHub API client 区分）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthHttpClient
