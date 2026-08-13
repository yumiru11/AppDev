package com.yumiru11.githubapp.feature.notifications.di

import com.yumiru11.githubapp.core.githubrest.api.NotificationApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 通知模块 Hilt 装配：NotificationApi 由 core:github-rest 的共享 Retrofit 创建。
 *
 * 刻意放在 feature 侧：core:github-rest 只新增接口文件，不改既有 RestNetworkModule 装配。
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {
    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create(NotificationApi::class.java)
}
