package com.yumiru11.githubapp.core.datastore.di

import com.yumiru11.githubapp.core.datastore.preferences.DefaultUserPreferencesRepository
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 用户偏好仓库装配（T6 Wave2 补 T5 遗留缺口）。
 *
 * [UserPreferencesRepository] 接口 KDoc 声明「Hilt @Binds 装配实现」，但 T5
 * 合入时未提供对应模块——此前无消费者注入该接口，HiltGraphTest 只注入
 * `DataStore<Preferences>` 未暴露缺口。MainActivity（本票）首次消费接口，
 * Dagger MissingBinding 报错。按与 core:github-data RepositoryModule 相同模式补齐。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserPreferencesRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: DefaultUserPreferencesRepository): UserPreferencesRepository
}
