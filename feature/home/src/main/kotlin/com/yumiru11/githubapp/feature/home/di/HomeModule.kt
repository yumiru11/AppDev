package com.yumiru11.githubapp.feature.home.di

import com.yumiru11.githubapp.core.githubrest.api.EventsApi
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.TrendApi
import com.yumiru11.githubapp.core.githubrest.api.TrendSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    /**
     * TrendApi 专用 Retrofit（L08）：**不复用共享 OkHttpClient**。
     *
     * 镜像站（raw.githubusercontent.com 第三方静态托管）与 api.github.com 不同源，共享客户端的
     * AuthTokenInterceptor 对任意 host 无差别注入 `Authorization: Bearer {token}`——
     * 把用户令牌发给第三方镜像属安全红线，故此处用**无拦截器的裸客户端**。
     * 超时与共享客户端一致收紧到 5s：超时由 TrendRepository 静默降级为搜索回退。
     */
    @Provides
    @Singleton
    fun provideTrendApi(): TrendApi =
        GitHubRestClient
            .createUnauthenticatedRetrofit(baseUrl = TrendSource.MIRROR_BASE_URL.toHttpUrl())
            .create(TrendApi::class.java)
}
