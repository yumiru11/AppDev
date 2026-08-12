package com.yumiru11.githubapp.core.githubdata.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import com.yumiru11.githubapp.core.githubdata.map.toDomain
import com.yumiru11.githubapp.core.githubgraphql.generated.RepositoryOverviewQuery
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [RepositoryRepository] 默认实现：GraphQL 读优先（NetworkOnly），失败降级 REST。
 *
 * GraphQL 查询不存在时 data.repository 为 null（可能伴随 errors 数组），
 * 此时与网络/HTTP 失败一样走 REST 兜底通道。
 */
class DefaultRepositoryRepository
    @Inject
    constructor(
        private val apolloClient: ApolloClient,
        private val repositoryApi: RepositoryApi,
    ) : RepositoryRepository {
        override suspend fun getRepository(
            owner: String,
            name: String,
        ): Repository {
            val repository =
                apolloClient
                    .query(RepositoryOverviewQuery(owner = owner, name = name))
                    .fetchPolicy(FetchPolicy.NetworkOnly)
                    .execute()
                    .data
                    ?.repository
            if (repository != null) return repository.toDomain(ownerLogin = owner)

            return try {
                repositoryApi.getRepository(owner, name).toDomain()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") t: Throwable,
            ) {
                // REST 通道异常统一归一化（HttpException/IOException/未知），故抑制 TooGenericExceptionCaught
                throw GitHubRequestException(t.asGitHubError(), t)
            }
        }
    }
