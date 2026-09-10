package com.yumiru11.githubapp.feature.search.di

import com.yumiru11.githubapp.feature.search.data.SearchResultCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** 搜索结果缓存的静默刷新作用域（应用级，见 [provideSearchCacheScope]） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SearchCacheScope

/**
 * 搜索缓存装配（issue #165 / L13）。
 *
 * - [SearchResultCache]：单例 → 同一 Session 内跨 ViewModel 存活（切 Tab/进出搜索页仍命中）
 * - 缓存静默刷新作用域：应用级 SupervisorJob + Default，独立于任何 ViewModel 生命周期
 *   （用户离开搜索页后回填仍可完成；单个子任务失败不影响其他刷新）
 */
@Module
@InstallIn(SingletonComponent::class)
object SearchCacheModule {
    @Provides
    @Singleton
    fun provideSearchResultCache(): SearchResultCache = SearchResultCache()

    @Provides
    @Singleton
    @SearchCacheScope
    fun provideSearchCacheScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
