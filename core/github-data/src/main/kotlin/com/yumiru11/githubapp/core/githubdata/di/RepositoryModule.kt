package com.yumiru11.githubapp.core.githubdata.di

import com.yumiru11.githubapp.core.githubdata.repository.DefaultRepositoryRepository
import com.yumiru11.githubapp.core.githubdata.repository.RepositoryRepository
import com.yumiru11.githubapp.core.githubdata.user.DefaultUserRepository
import com.yumiru11.githubapp.core.githubdata.user.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓库层装配：接口 → 默认实现（UI 只依赖抽象）。
 *
 * REST/GraphQL 通道（UserApi/RepositoryApi/ApolloClient）由
 * core:github-rest 的 RestNetworkModule 与 core:github-graphql 的 GraphqlModule 提供。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: DefaultUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindRepositoryRepository(impl: DefaultRepositoryRepository): RepositoryRepository
}
