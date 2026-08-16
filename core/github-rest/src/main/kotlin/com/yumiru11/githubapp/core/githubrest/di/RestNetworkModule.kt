package com.yumiru11.githubapp.core.githubrest.di

import com.yumiru11.githubapp.core.common.GitHubApiConfig
import com.yumiru11.githubapp.core.githubrest.BuildConfig
import com.yumiru11.githubapp.core.githubrest.api.ContentApi
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.api.GitTreeApi
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.api.ReadmeApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import com.yumiru11.githubapp.core.githubrest.http.EtagStore
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * REST 通道 Hilt 装配（SingletonComponent）。
 *
 * [TokenProvider] 由 app 装配层提供（SessionTokenProvider，读 TokenStorage；
 * core:github-rest 不依赖 core:github-auth，P0-7 修复：原实现绑死 GuestTokenProvider
 * 导致 PAT/登录令牌永不注入请求，2026-08-14 真机走查）。
 */
@Module
@InstallIn(SingletonComponent::class)
object RestNetworkModule {
    @Provides
    @Singleton
    fun provideEtagStore(): EtagStore = InMemoryEtagStore()

    @Provides
    @Singleton
    fun provideJson(): Json = GitHubRestClient.createJson()

    @Provides
    @Singleton
    @GitHubHttpClient
    fun provideGitHubOkHttpClient(
        tokenProvider: TokenProvider,
        etagStore: EtagStore,
    ): OkHttpClient =
        GitHubRestClient.createOkHttpClient(
            tokenProvider = tokenProvider,
            etagStore = etagStore,
            debugLogging = BuildConfig.DEBUG,
        )

    @Provides
    @Singleton
    fun provideRetrofit(
        @GitHubHttpClient client: OkHttpClient,
        json: Json,
    ): Retrofit =
        GitHubRestClient.createRetrofit(
            baseUrl = GitHubApiConfig.REST_BASE_URL.toHttpUrl(),
            client = client,
            json = json,
        )

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideRepositoryApi(retrofit: Retrofit): RepositoryApi = retrofit.create(RepositoryApi::class.java)

    @Provides
    @Singleton
    fun provideReadmeApi(retrofit: Retrofit): ReadmeApi = retrofit.create(ReadmeApi::class.java)

    @Provides
    @Singleton
    fun provideIssueApi(retrofit: Retrofit): IssueApi = retrofit.create(IssueApi::class.java)

    @Provides
    @Singleton
    fun provideGitTreeApi(retrofit: Retrofit): GitTreeApi = retrofit.create(GitTreeApi::class.java)

    @Provides
    @Singleton
    fun provideContentApi(retrofit: Retrofit): ContentApi = retrofit.create(ContentApi::class.java)

    @Provides
    @Singleton
    fun provideSearchApi(retrofit: Retrofit): SearchApi = retrofit.create(SearchApi::class.java)
}
