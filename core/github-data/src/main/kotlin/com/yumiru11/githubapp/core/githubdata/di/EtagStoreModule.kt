package com.yumiru11.githubapp.core.githubdata.di

import com.yumiru11.githubapp.core.database.dao.EtagCacheDao
import com.yumiru11.githubapp.core.githubauth.session.SessionCacheCleaner
import com.yumiru11.githubapp.core.githubdata.cache.RoomEtagStore
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagScopeProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagStore
import com.yumiru11.githubapp.core.githubrest.http.TokenEtagScopeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * 持久化 ETag 缓存装配（DATA-1）。
 *
 * [EtagStore] 的持久实现放在数据装配层（core:github-data，plan.md §10.2 缓存职责），
 * 网络层 core:github-rest 只持抽象；因此本模块同时提供跨进程实现的 [EtagStore]、
 * 按凭据派生作用域的 [EtagScopeProvider]，以及登出清空用的 [SessionCacheCleaner]（同一实例）。
 */
@Module
@InstallIn(SingletonComponent::class)
object EtagStoreModule {
    @Provides
    @Singleton
    fun provideRoomEtagStore(dao: EtagCacheDao): RoomEtagStore = RoomEtagStore(dao)

    @Provides
    @Singleton
    fun provideEtagStore(store: RoomEtagStore): EtagStore = store

    @Provides
    @Singleton
    fun provideEtagScopeProvider(tokenProvider: TokenProvider): EtagScopeProvider = TokenEtagScopeProvider(tokenProvider)

    @Provides
    @IntoSet
    fun provideSessionCacheCleaner(store: RoomEtagStore): SessionCacheCleaner = store
}
