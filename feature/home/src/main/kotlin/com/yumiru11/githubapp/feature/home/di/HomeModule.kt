package com.yumiru11.githubapp.feature.home.di

import com.yumiru11.githubapp.core.githubrest.api.EventsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 首页动态流模块 Hilt 装配：EventsApi 由 core:github-rest 的共享 Retrofit 创建。
 *
 * 刻意放在 feature 侧：core:github-rest 只新增接口文件，不改既有 RestNetworkModule 装配
 * （同 NotificationsModule 先例）。UserApi 已由 RestNetworkModule 提供。
 */
@Module
@InstallIn(SingletonComponent::class)
object HomeModule {
    @Provides
    @Singleton
    fun provideEventsApi(retrofit: Retrofit): EventsApi = retrofit.create(EventsApi::class.java)
}
