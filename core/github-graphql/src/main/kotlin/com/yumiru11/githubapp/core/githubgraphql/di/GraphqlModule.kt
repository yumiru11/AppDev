package com.yumiru11.githubapp.core.githubgraphql.di

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.yumiru11.githubapp.core.common.GitHubApiConfig
import com.yumiru11.githubapp.core.githubgraphql.GitHubApolloClientFactory
import com.yumiru11.githubapp.core.githubrest.di.GitHubHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * GraphQL 通道 Hilt 装配（SingletonComponent）。
 *
 * 复用 @GitHubHttpClient（统一请求头/Auth/ETag 拦截器链，issue #6 共享 OkHttp），
 * Normalized Cache 走 memory → SQLite 链（plan.md §4.4，Apollo 5 新版独立缓存库）。
 */
@Module
@InstallIn(SingletonComponent::class)
object GraphqlModule {
    private const val APOLLO_DB_NAME = "github-graphql-cache.db"

    @Provides
    @Singleton
    fun provideApolloClient(
        @GitHubHttpClient okHttpClient: OkHttpClient,
        @ApplicationContext context: Context,
    ): ApolloClient =
        GitHubApolloClientFactory.create(
            serverUrl = GitHubApiConfig.GRAPHQL_URL,
            okHttpClient = okHttpClient,
            persistentCacheFactory = SqlNormalizedCacheFactory(context, APOLLO_DB_NAME),
        )
}
