package com.yumiru11.githubapp.feature.profile.di

import com.yumiru11.githubapp.core.githubrest.api.GistApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 个人页模块 Hilt 装配：GistApi 由 core:github-rest 的共享 Retrofit 创建。
 *
 * 刻意放在 feature 侧（同 HomeModule/NotificationsModule 先例）：
 * core:github-rest 只新增接口文件，不改既有 RestNetworkModule 装配。
 * UserApi 已由 RestNetworkModule 提供。
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    @Singleton
    fun provideGistApi(retrofit: Retrofit): GistApi = retrofit.create(GistApi::class.java)
}
