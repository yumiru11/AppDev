package com.yumiru11.githubapp.di

import com.yumiru11.githubapp.core.githubauth.auth.AuthHttpClient
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * app 装配层 DI 补充（T4 Wave2）。
 *
 * **合并代码缺口**：core:github-auth 的 [OkHttpTokenEndpointClient] 构造注入
 * **未限定** OkHttpClient，但其 AuthConfigModule 只提供 `@AuthHttpClient` 限定绑定
 * （限定符漏标在构造函数参数上）——app 首次依赖 core:github-auth 组全图即 MissingBinding。
 *
 * 本模块在装配层补桥：未限定 key 委托给 `@AuthHttpClient` 裸客户端（token 端点请求
 * 不注入凭据，语义与 core 设计一致）。根治应在 core:github-auth 构造函数参数补
 * `@AuthHttpClient`（已报告，待决策；本桥不影响根治后删除）。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppDiModule {
    @Provides
    @Singleton
    fun provideTokenProvider(provider: SessionTokenProvider): TokenProvider = provider

    @Provides
    @Singleton
    fun provideTokenEndpointOkHttpClient(
        @AuthHttpClient client: OkHttpClient,
    ): OkHttpClient = client
}
