package com.yumiru11.githubapp.core.githubrest.http

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 限流快照 Hilt 装配（issue #165 / L13）。
 *
 * 绑定返回进程级单例 [ProcessRateLimitStore]——拦截器侧（[EtagCacheInterceptor]）直接引用
 * 同一对象完成录制，消费方（feature:search 限流提示条）按 [RateLimitStore] 接口注入，
 * 单测可自行构造 [InMemoryRateLimitStore] 替换。
 */
@Module
@InstallIn(SingletonComponent::class)
object RateLimitModule {
    @Provides
    @Singleton
    fun provideRateLimitStore(): RateLimitStore = ProcessRateLimitStore
}
